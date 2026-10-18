package dev.booking.sports.shared.upload.r2;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import dev.booking.sports.shared.exception.ApiException;
import dev.booking.sports.shared.upload.ObjectStorage;
import dev.booking.sports.shared.upload.UploadErrorCode;

@Component
@ConditionalOnProperty(prefix = "app.storage.r2", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledObjectStorage implements ObjectStorage {
    private ApiException disabled() { return new ApiException(UploadErrorCode.UPLOAD_UNAVAILABLE); }
    public PutGrant presignPut(String key, String type, String uploadId, Duration ttl) { throw disabled(); }
    public Metadata head(String key) { throw disabled(); }
    public byte[] readForValidation(String key, String etag, int limit) { throw disabled(); }
    public void promote(String source, String destination, String etag) { throw disabled(); }
    public URI presignGet(String key, Duration ttl) { throw disabled(); }
    public void delete(String key) { throw disabled(); }
}
