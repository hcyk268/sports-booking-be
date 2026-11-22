package dev.booking.sports.shared.upload;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import dev.booking.sports.shared.exception.ApiException;
import static dev.booking.sports.shared.upload.UploadAsset.Status.*;
import static dev.booking.sports.shared.upload.UploadContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class UploadServiceTest {
    private final UploadAssetRepository repository = mock(UploadAssetRepository.class);
    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final UploadPolicy policy = mock(UploadPolicy.class);
    private final UUID owner = UUID.randomUUID(), resource = UUID.randomUUID(), id = UUID.randomUUID();
    private UploadService service;
    private ObjectStorageRegistry storages;
    private UploadAsset asset;
    private byte[] png;

    @BeforeEach void setup() throws Exception {
        when(policy.purpose()).thenReturn("COURT_IMAGE");
        when(policy.maxBytes()).thenReturn(5242880L);
        when(policy.contentTypes()).thenReturn(Set.of("image/png", "image/jpeg"));
        when(storage.provider()).thenReturn(StorageProvider.R2);
        storages = new ObjectStorageRegistry(List.of(storage), "r2");
        clearInvocations(storage);
        service = new UploadService(repository, storages, new ImageVerifier(), List.of(policy));
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        png = output.toByteArray();
        asset = new UploadAsset();
        asset.setId(id); asset.setOwnerId(owner); asset.setResourceId(resource); asset.setPurpose("COURT_IMAGE");
        asset.setStatus(PENDING); asset.setContentType("image/png"); asset.setSizeBytes(png.length);
        asset.setStagingKey("staging/" + id); asset.setStorageKey("assets/" + id + ".png");
        asset.setExpiresAt(Instant.now().plusSeconds(900));
        when(repository.lockById(id)).thenReturn(Optional.of(asset));
    }
    private void storedPng() {
        when(storage.head(asset.getStagingKey())).thenReturn(new ObjectStorage.Metadata(png.length, "image/png", "\"version-a\"", Map.of("upload-id", id.toString())));
        when(storage.readForValidation(asset.getStagingKey(), "\"version-a\"", png.length)).thenReturn(png);
    }
    private void error(Runnable action, UploadErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(code));
    }
    @Test void initiationAuthorizesAndNeverExposesFinalWriteKey() {
        when(repository.saveAndFlush(any())).thenAnswer(call -> { UploadAsset value = call.getArgument(0); value.setId(id); return value; });
        when(storage.presignPut(anyString(), eq("image/png"), eq(id.toString()), eq(Duration.ofMinutes(5))))
                .thenReturn(new ObjectStorage.PutGrant(URI.create("https://example.r2.cloudflarestorage.com/staging/test"), Map.of("Content-Type", "image/png")));
        var ticket = service.initiate(owner, new InitiateRequest("COURT_IMAGE", resource, "a.png", "image/png", png.length));
        assertThat(ticket.uploadId()).isEqualTo(id);
        assertThat(ticket.uploadUrl().getPath()).startsWith("/staging/");
        verify(policy).authorize(owner, resource);
        verify(policy).checkCapacity(resource);
    }
    @Test void oversizeAndUnregisteredPurposeFailBeforeStorage() {
        error(() -> service.initiate(owner, new InitiateRequest("COURT_IMAGE", resource, "a.png", "image/png", 5242881)), UploadErrorCode.UPLOAD_TOO_LARGE);
        error(() -> service.initiate(owner, new InitiateRequest("UNREGISTERED", resource, "a.png", "image/png", 1)), UploadErrorCode.UPLOAD_INVALID);
        verifyNoInteractions(storage);
    }
    @Test void wrongOwnerCannotVerifyOrCancel() {
        error(() -> service.complete(UUID.randomUUID(), id), UploadErrorCode.UPLOAD_NOT_FOUND);
        error(() -> service.cancel(UUID.randomUUID(), id), UploadErrorCode.UPLOAD_NOT_FOUND);
        verifyNoInteractions(storage);
    }
    @Test void expiredUploadCannotCompleteOrAttach() {
        asset.setExpiresAt(Instant.now().minusSeconds(1));
        error(() -> service.complete(owner, id), UploadErrorCode.UPLOAD_EXPIRED);
        error(() -> service.claim(owner, id, "COURT_IMAGE", resource), UploadErrorCode.UPLOAD_EXPIRED);
        verifyNoInteractions(storage);
    }
    @Test void metadataIsReadFromStorageAndMustMatchTheTicket() {
        when(storage.head(asset.getStagingKey())).thenReturn(new ObjectStorage.Metadata(png.length + 1, "image/png", "etag", Map.of("upload-id", id.toString())));
        error(() -> service.complete(owner, id), UploadErrorCode.UPLOAD_INVALID);
        verify(storage, never()).readForValidation(anyString(), anyString(), anyInt());
        assertThat(asset.getStatus()).isEqualTo(PENDING);
    }
    @Test void missingSignedUploadIdentityIsRejected() {
        when(storage.head(asset.getStagingKey())).thenReturn(new ObjectStorage.Metadata(png.length, "image/png", "etag", Map.of()));
        error(() -> service.complete(owner, id), UploadErrorCode.UPLOAD_INVALID);
        verify(storage, never()).promote(anyString(), anyString(), anyString());
    }
    @Test void fakePngIsRejectedDespiteCorrectMimeMetadata() {
        storedPng();
        when(storage.readForValidation(anyString(), anyString(), anyInt())).thenReturn(new byte[png.length]);
        error(() -> service.complete(owner, id), UploadErrorCode.UPLOAD_INVALID);
        verify(storage, never()).promote(anyString(), anyString(), anyString());
    }
    @Test void validImageIsPromotedUsingTheVerifiedEtagAndCompletionIsIdempotent() {
        storedPng();
        assertThat(service.complete(owner, id).status()).isEqualTo("READY");
        assertThat(service.complete(owner, id).status()).isEqualTo("READY");
        verify(storage, times(1)).promote(asset.getStagingKey(), asset.getStorageKey(), "\"version-a\"");
        verify(storage, times(1)).head(asset.getStagingKey());
    }
    @Test void objectReplacementDuringVerificationDoesNotProduceReadyAsset() {
        storedPng();
        doThrow(new ApiException(UploadErrorCode.UPLOAD_CHANGED)).when(storage).promote(anyString(), anyString(), anyString());
        error(() -> service.complete(owner, id), UploadErrorCode.UPLOAD_CHANGED);
        assertThat(asset.getStatus()).isEqualTo(PENDING);
    }
    @Test void onlyReadyAssetForSameOwnerPurposeAndResourceCanBeClaimedOnce() {
        error(() -> service.claim(owner, id, "COURT_IMAGE", resource), UploadErrorCode.UPLOAD_NOT_READY);
        asset.setStatus(READY);
        error(() -> service.claim(owner, id, "AVATAR", resource), UploadErrorCode.UPLOAD_NOT_FOUND);
        error(() -> service.claim(owner, id, "COURT_IMAGE", UUID.randomUUID()), UploadErrorCode.UPLOAD_NOT_FOUND);
        assertThat(service.claim(owner, id, "COURT_IMAGE", resource).id()).isEqualTo(id);
        assertThat(asset.getStatus()).isEqualTo(ATTACHED);
        error(() -> service.claim(owner, id, "COURT_IMAGE", resource), UploadErrorCode.UPLOAD_NOT_READY);
    }
    @Test void attachedUploadCannotBeCancelledButReleaseIsDurable() {
        asset.setStatus(ATTACHED);
        error(() -> service.cancel(owner, id), UploadErrorCode.UPLOAD_NOT_READY);
        service.release(id);
        assertThat(asset.getStatus()).isEqualTo(DELETE_PENDING);
        verifyNoInteractions(storage);
    }
    @Test void pendingAndReadyUploadsNeverReceiveReadUrl() {
        when(repository.findById(id)).thenReturn(Optional.of(asset));
        error(() -> service.readUrl(id), UploadErrorCode.UPLOAD_NOT_FOUND);
        asset.setStatus(READY);
        error(() -> service.readUrl(id), UploadErrorCode.UPLOAD_NOT_FOUND);
        asset.setStatus(ATTACHED);
        when(storage.presignGet(asset.getStorageKey(), Duration.ofSeconds(60))).thenReturn(URI.create("https://example.r2.cloudflarestorage.com/asset"));
        assertThat(service.readUrl(id)).isNotNull();
    }
    @Test void cleanupRetriesStorageFailureWithoutLosingPendingDeletion() {
        asset.setStatus(DELETE_PENDING);
        var cleanup = new UploadCleanup(repository, storages);
        doThrow(new ApiException(UploadErrorCode.UPLOAD_UNAVAILABLE)).when(storage).delete(asset.getStorageKey());
        error(() -> cleanup.clean(id), UploadErrorCode.UPLOAD_UNAVAILABLE);
        assertThat(asset.getStatus()).isEqualTo(DELETE_PENDING);
        doNothing().when(storage).delete(asset.getStorageKey());
        cleanup.clean(id);
        assertThat(asset.getStatus()).isEqualTo(DELETED);
    }
    @Test void newS3UploadsPersistProviderAndUseS3ForCompletionReadAndCleanup() {
        var s3 = mock(ObjectStorage.class);
        when(s3.provider()).thenReturn(StorageProvider.S3);
        var registry = new ObjectStorageRegistry(List.of(storage, s3), "s3");
        var s3Service = new UploadService(repository, registry, new ImageVerifier(), List.of(policy));
        when(repository.saveAndFlush(any())).thenAnswer(call -> {
            asset = call.getArgument(0); asset.setId(id); return asset;
        });
        when(s3.presignPut(anyString(), anyString(), eq(id.toString()), any()))
                .thenReturn(new ObjectStorage.PutGrant(URI.create("https://bucket.s3.ap-southeast-1.amazonaws.com/staging/id"), Map.of()));
        s3Service.initiate(owner, new InitiateRequest("COURT_IMAGE", resource, "a.png", "image/png", png.length));
        assertThat(asset.getStorageProvider()).isEqualTo(StorageProvider.S3);
        when(repository.lockById(id)).thenAnswer(call -> Optional.of(asset));
        when(s3.head(asset.getStagingKey())).thenReturn(new ObjectStorage.Metadata(png.length, "image/png", "etag", Map.of("upload-id", id.toString())));
        when(s3.readForValidation(asset.getStagingKey(), "etag", png.length)).thenReturn(png);
        assertThat(s3Service.complete(owner, id).status()).isEqualTo("READY");
        verify(s3).promote(asset.getStagingKey(), asset.getStorageKey(), "etag");
        s3Service.claim(owner, id, "COURT_IMAGE", resource);
        when(repository.findById(id)).thenReturn(Optional.of(asset));
        URI url = URI.create("https://bucket.s3.ap-southeast-1.amazonaws.com/assets/id.png");
        when(s3.presignGet(asset.getStorageKey(), Duration.ofSeconds(60))).thenReturn(url);
        assertThat(s3Service.readUrl(id)).isEqualTo(url);
        s3Service.release(id);
        new UploadCleanup(repository, registry).clean(id);
        verify(s3).delete(asset.getStagingKey());
        verify(s3).delete(asset.getStorageKey());
        assertThat(asset.getStatus()).isEqualTo(DELETED);
        verify(storage, never()).head(anyString());
        verify(storage, never()).presignGet(anyString(), any());
        verify(storage, never()).delete(anyString());
    }

    @Test void switchingNewUploadsToS3KeepsExistingAssetsOnR2() {
        var s3 = mock(ObjectStorage.class);
        when(s3.provider()).thenReturn(StorageProvider.S3);
        var registry = new ObjectStorageRegistry(List.of(storage, s3), "s3");
        var switched = new UploadService(repository, registry, new ImageVerifier(), List.of(policy));
        clearInvocations(s3);
        storedPng();
        switched.complete(owner, id);
        verify(storage).promote(asset.getStagingKey(), asset.getStorageKey(), "\"version-a\"");
        asset.setStatus(ATTACHED);
        when(repository.findById(id)).thenReturn(Optional.of(asset));
        switched.readUrl(id);
        verify(storage).presignGet(asset.getStorageKey(), Duration.ofSeconds(60));
        switched.release(id);
        new UploadCleanup(repository, registry).clean(id);
        verify(storage).delete(asset.getStagingKey());
        verify(storage).delete(asset.getStorageKey());
        verifyNoInteractions(s3);
    }

    @Test void disabledStorageFailsBeforeSavingAnUpload() {
        var disabled = new UploadService(repository, new ObjectStorageRegistry(List.of(), "s3"), new ImageVerifier(), List.of(policy));
        error(() -> disabled.initiate(owner, new InitiateRequest("COURT_IMAGE", resource, "a.png", "image/png", 1)), UploadErrorCode.UPLOAD_UNAVAILABLE);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test void cleanupNeverDeletesAttachedOrUnexpiredAssets() {
        var cleanup = new UploadCleanup(repository, storages);
        cleanup.clean(id);
        asset.setStatus(ATTACHED);
        asset.setExpiresAt(Instant.now().minusSeconds(1));
        cleanup.clean(id);
        verifyNoInteractions(storage);
    }
}
