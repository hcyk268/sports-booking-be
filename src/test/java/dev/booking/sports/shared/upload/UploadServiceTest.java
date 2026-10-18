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
    private UploadAsset asset;
    private byte[] png;

    @BeforeEach void setup() throws Exception {
        when(policy.purpose()).thenReturn("COURT_IMAGE");
        when(policy.maxBytes()).thenReturn(5242880L);
        when(policy.contentTypes()).thenReturn(Set.of("image/png", "image/jpeg"));
        service = new UploadService(repository, storage, new ImageVerifier(), List.of(policy));
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
        var cleanup = new UploadCleanup(repository, storage);
        doThrow(new ApiException(UploadErrorCode.UPLOAD_UNAVAILABLE)).when(storage).delete(asset.getStorageKey());
        error(() -> cleanup.clean(id), UploadErrorCode.UPLOAD_UNAVAILABLE);
        assertThat(asset.getStatus()).isEqualTo(DELETE_PENDING);
        doNothing().when(storage).delete(asset.getStorageKey());
        cleanup.clean(id);
        assertThat(asset.getStatus()).isEqualTo(DELETED);
    }
    @Test void cleanupNeverDeletesAttachedOrUnexpiredAssets() {
        var cleanup = new UploadCleanup(repository, storage);
        cleanup.clean(id);
        asset.setStatus(ATTACHED);
        asset.setExpiresAt(Instant.now().minusSeconds(1));
        cleanup.clean(id);
        verifyNoInteractions(storage);
    }
}
