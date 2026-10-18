package dev.booking.sports.court.application;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.booking.sports.court.api.dto.response.CourtImageResponse;
import dev.booking.sports.court.api.error.CourtErrorCode;
import dev.booking.sports.court.domain.enums.CourtStatus;
import dev.booking.sports.court.domain.model.Court;
import dev.booking.sports.court.domain.model.CourtImage;
import dev.booking.sports.court.domain.repository.CourtRepository;
import dev.booking.sports.court.domain.repository.CourtImageRepository;
import dev.booking.sports.shared.exception.ApiException;
import dev.booking.sports.shared.upload.UploadService;
import dev.booking.sports.shared.upload.legacy.LegacyImageReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class CourtImageService {
    private final CourtRepository courtRepository;
    private final CourtImageRepository imageRepository;
    private final UploadService uploads;
    private final LegacyImageReader legacy;
    @PersistenceContext private EntityManager entityManager;

    @Transactional
    public CourtImageResponse attach(UUID ownerId, UUID courtId, UUID uploadId) {
        Court court = ownedCourt(ownerId, courtId);
        entityManager.lock(court, LockModeType.PESSIMISTIC_WRITE);
        var existing = imageRepository.findByUploadId(uploadId);
        if (existing.isPresent()) {
            if (!existing.get().getCourt().getId().equals(courtId))
                throw new ApiException(CourtErrorCode.COURT_IMAGE_NOT_FOUND);
            return CourtImageResponse.forOwner(existing.get());
        }
        List<CourtImage> gallery = gallery(courtId);
        if (gallery.size() >= 10) throw new ApiException(CourtErrorCode.COURT_IMAGE_LIMIT);
        var claimed = uploads.claim(ownerId, uploadId, CourtImageUploadPolicy.PURPOSE, courtId);
        CourtImage image = new CourtImage();
        image.setCourt(court);
        image.setUploadId(claimed.id());
        image.setStorageKey(claimed.storageKey());
        image.setContentType(claimed.contentType());
        image.setSizeBytes(claimed.sizeBytes());
        image.setDisplayOrder(gallery.stream().mapToInt(CourtImage::getDisplayOrder).max().orElse(-1) + 1);
        image.setCover(gallery.isEmpty());
        return CourtImageResponse.forOwner(imageRepository.saveAndFlush(image));
    }
    @Transactional(readOnly = true)
    public List<CourtImageResponse> listOwned(UUID ownerId, UUID courtId) {
        ownedCourt(ownerId, courtId);
        return gallery(courtId).stream().map(CourtImageResponse::forOwner).toList();
    }
    @Transactional(readOnly = true)
    public List<CourtImageResponse> listPublic(UUID courtId) {
        activeCourt(courtId);
        return gallery(courtId).stream().map(CourtImageResponse::from).toList();
    }
    @Transactional(readOnly = true)
    public dev.booking.sports.shared.upload.UploadContracts.ReadAccess accessOwned(UUID ownerId, UUID courtId, UUID imageId) {
        ownedCourt(ownerId, courtId);
        CourtImage selected = image(courtId, imageId);
        return new dev.booking.sports.shared.upload.UploadContracts.ReadAccess(
                selected.getUploadId() == null ? null : uploads.readUrl(selected.getUploadId()));
    }
    @Transactional(readOnly = true)
    public ImageContent readOwned(UUID ownerId, UUID courtId, UUID imageId) {
        ownedCourt(ownerId, courtId);
        return content(image(courtId, imageId));
    }
    @Transactional(readOnly = true)
    public ImageContent readPublic(UUID courtId, UUID imageId) {
        activeCourt(courtId);
        return content(image(courtId, imageId));
    }
    @Transactional
    public CourtImageResponse setCover(UUID ownerId, UUID courtId, UUID imageId) {
        Court court = ownedCourt(ownerId, courtId);
        entityManager.lock(court, LockModeType.PESSIMISTIC_WRITE);
        CourtImage selected = image(courtId, imageId);
        if (!selected.isCover()) {
            gallery(courtId).forEach(item -> item.setCover(false));
            imageRepository.flush();
            selected.setCover(true);
            imageRepository.flush();
        }
        return CourtImageResponse.forOwner(selected);
    }
    @Transactional
    public void delete(UUID ownerId, UUID courtId, UUID imageId) {
        Court court = ownedCourt(ownerId, courtId);
        entityManager.lock(court, LockModeType.PESSIMISTIC_WRITE);
        CourtImage selected = image(courtId, imageId);
        boolean wasCover = selected.isCover();
        if (selected.getUploadId() != null) uploads.release(selected.getUploadId());
        imageRepository.delete(selected);
        imageRepository.flush();
        if (wasCover) {
            var remaining = gallery(courtId);
            if (!remaining.isEmpty()) { remaining.getFirst().setCover(true); imageRepository.flush(); }
        }
        // Legacy files are retained for rollback/manual migration, never silently removed.
    }
    private Court ownedCourt(UUID actor, UUID courtId) {
        return courtRepository.findByIdAndOwnerId(courtId, actor)
                .orElseThrow(() -> new ApiException(CourtErrorCode.COURT_NOT_FOUND));
    }
    private void activeCourt(UUID courtId) {
        courtRepository.findByIdAndStatus(courtId, CourtStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(CourtErrorCode.COURT_NOT_FOUND));
    }
    private CourtImage image(UUID courtId, UUID imageId) {
        return imageRepository.findByIdAndCourt_Id(imageId, courtId)
                .orElseThrow(() -> new ApiException(CourtErrorCode.COURT_IMAGE_NOT_FOUND));
    }
    private List<CourtImage> gallery(UUID courtId) {
        return imageRepository.findByCourt_IdOrderByDisplayOrderAscIdAsc(courtId);
    }
    private ImageContent content(CourtImage image) {
        return image.getUploadId() == null
                ? new ImageContent(legacy.read(image.getStorageKey()), image.getContentType(), image.getStorageKey(), null)
                : new ImageContent(null, image.getContentType(), image.getId().toString(), uploads.readUrl(image.getUploadId()));
    }
    public record ImageContent(byte[] bytes, String contentType, String fileName, URI redirect) {}
}
