package dev.booking.sports.shared.upload;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import jakarta.validation.constraints.*;

public final class UploadContracts {
    private UploadContracts() {}
    public record InitiateRequest(
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,49}") String purpose,
            @NotNull UUID resourceId,
            @NotBlank @Size(max = 120) String fileName,
            @NotBlank @Size(max = 40) String contentType,
            @Positive @Max(5242880) long sizeBytes) {}
    public record Ticket(UUID uploadId, URI uploadUrl, String method,
                         Map<String, String> headers, Instant expiresAt) {}
    public record VerifiedUpload(UUID uploadId, String contentType, long sizeBytes, String status) {
        static VerifiedUpload from(UploadAsset asset) {
            return new VerifiedUpload(asset.getId(), asset.getContentType(), asset.getSizeBytes(), asset.getStatus().name());
        }
    }
    public record ReadAccess(URI url) {}
    public record ClaimedUpload(UUID id, String storageKey, String contentType, long sizeBytes) {}
}
