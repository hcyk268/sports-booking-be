package dev.booking.sports.shared.upload;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import dev.booking.sports.shared.exception.ApiException;
import static dev.booking.sports.shared.upload.UploadContracts.*;
import static dev.booking.sports.shared.upload.UploadAsset.Status.*;

@Service
public class UploadService {
    private final UploadAssetRepository repository;
    private final ObjectStorage storage;
    private final ImageVerifier verifier;
    private final Map<String, UploadPolicy> policies;

    public UploadService(UploadAssetRepository repository, ObjectStorage storage,
                         ImageVerifier verifier, List<UploadPolicy> policies) {
        this.repository = repository;
        this.storage = storage;
        this.verifier = verifier;
        this.policies = policies.stream().collect(Collectors.toUnmodifiableMap(UploadPolicy::purpose, Function.identity()));
    }

    @Transactional
    public Ticket initiate(UUID ownerId, InitiateRequest request) {
        UploadPolicy policy = policy(request.purpose());
        policy.authorize(ownerId, request.resourceId());
        policy.checkCapacity(request.resourceId());
        if (request.sizeBytes() < 1 || request.sizeBytes() > Math.min(policy.maxBytes(), 5242880))
            throw new ApiException(UploadErrorCode.UPLOAD_TOO_LARGE);
        if (!policy.contentTypes().contains(request.contentType()) || request.fileName() == null
                || request.fileName().isBlank() || request.fileName().length() > 120
                || request.fileName().chars().anyMatch(Character::isISOControl))
            throw new ApiException(UploadErrorCode.UPLOAD_INVALID);
        Instant now = Instant.now();
        if (repository.countByOwnerIdAndStatusInAndExpiresAtAfter(ownerId, List.of(PENDING, READY), now) >= 20)
            throw new ApiException(UploadErrorCode.UPLOAD_LIMIT);
        UploadAsset asset = new UploadAsset();
        asset.setOwnerId(ownerId);
        asset.setPurpose(policy.purpose());
        asset.setResourceId(request.resourceId());
        asset.setFileName(request.fileName());
        asset.setContentType(request.contentType());
        asset.setSizeBytes(request.sizeBytes());
        String keyId = UUID.randomUUID().toString();
        asset.setStagingKey("staging/" + keyId);
        asset.setStorageKey("assets/" + keyId + (request.contentType().equals("image/png") ? ".png" : ".jpg"));
        asset.setStatus(PENDING);
        asset.setExpiresAt(now.plus(Duration.ofMinutes(15)));
        repository.saveAndFlush(asset);
        var grant = storage.presignPut(asset.getStagingKey(), asset.getContentType(), asset.getId().toString(), Duration.ofMinutes(5));
        return new Ticket(asset.getId(), grant.url(), "PUT", grant.headers(), now.plus(Duration.ofMinutes(5)));
    }

    @Transactional
    public VerifiedUpload complete(UUID ownerId, UUID uploadId) {
        UploadAsset asset = ownedLocked(ownerId, uploadId);
        policy(asset.getPurpose()).authorize(ownerId, asset.getResourceId());
        if (asset.getStatus() == ATTACHED) return VerifiedUpload.from(asset);
        requireUnexpired(asset);
        if (asset.getStatus() == READY) return VerifiedUpload.from(asset);
        if (asset.getStatus() != PENDING) throw new ApiException(UploadErrorCode.UPLOAD_NOT_READY);
        var metadata = storage.head(asset.getStagingKey());
        if (metadata.sizeBytes() != asset.getSizeBytes()
                || !asset.getContentType().equals(metadata.contentType())
                || !asset.getId().toString().equals(metadata.metadata().get("upload-id"))
                || metadata.etag() == null || metadata.etag().isBlank())
            throw new ApiException(UploadErrorCode.UPLOAD_INVALID);
        byte[] bytes = storage.readForValidation(asset.getStagingKey(), metadata.etag(), (int) asset.getSizeBytes());
        if (bytes.length != asset.getSizeBytes()) throw new ApiException(UploadErrorCode.UPLOAD_INVALID);
        verifier.verify(bytes, asset.getContentType());
        // Conditional copy freezes the checked bytes at a key the browser cannot write.
        storage.promote(asset.getStagingKey(), asset.getStorageKey(), metadata.etag());
        asset.setEtag(metadata.etag());
        asset.setStatus(READY);
        return VerifiedUpload.from(asset);
    }

    /** Called by the consumer while holding its own resource lock, in the same DB transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public ClaimedUpload claim(UUID ownerId, UUID uploadId, String purpose, UUID resourceId) {
        UploadAsset asset = ownedLocked(ownerId, uploadId);
        if (!asset.getPurpose().equals(purpose) || !asset.getResourceId().equals(resourceId))
            throw new ApiException(UploadErrorCode.UPLOAD_NOT_FOUND);
        requireUnexpired(asset);
        if (asset.getStatus() != READY) throw new ApiException(UploadErrorCode.UPLOAD_NOT_READY);
        asset.setStatus(ATTACHED);
        return new ClaimedUpload(asset.getId(), asset.getStorageKey(), asset.getContentType(), asset.getSizeBytes());
    }

    /** Consumer must authorize read access before calling this internal API. */
    @Transactional(readOnly = true)
    public URI readUrl(UUID uploadId) {
        UploadAsset asset = repository.findById(uploadId).orElseThrow(() -> new ApiException(UploadErrorCode.UPLOAD_NOT_FOUND));
        if (asset.getStatus() != ATTACHED) throw new ApiException(UploadErrorCode.UPLOAD_NOT_FOUND);
        return storage.presignGet(asset.getStorageKey(), Duration.ofSeconds(60));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void release(UUID uploadId) {
        UploadAsset asset = repository.lockById(uploadId).orElseThrow(() -> new ApiException(UploadErrorCode.UPLOAD_NOT_FOUND));
        asset.setStatus(DELETE_PENDING);
    }

    @Transactional
    public void cancel(UUID ownerId, UUID uploadId) {
        UploadAsset asset = ownedLocked(ownerId, uploadId);
        if (asset.getStatus() == ATTACHED) throw new ApiException(UploadErrorCode.UPLOAD_NOT_READY);
        if (asset.getStatus() != DELETED) asset.setStatus(DELETE_PENDING);
    }

    private UploadAsset ownedLocked(UUID ownerId, UUID id) {
        return repository.lockById(id).filter(asset -> asset.getOwnerId().equals(ownerId))
                .orElseThrow(() -> new ApiException(UploadErrorCode.UPLOAD_NOT_FOUND));
    }
    private void requireUnexpired(UploadAsset asset) {
        if (!asset.getExpiresAt().isAfter(Instant.now())) throw new ApiException(UploadErrorCode.UPLOAD_EXPIRED);
    }
    private UploadPolicy policy(String purpose) {
        UploadPolicy policy = policies.get(purpose);
        if (policy == null) throw new ApiException(UploadErrorCode.UPLOAD_INVALID);
        return policy;
    }
}
