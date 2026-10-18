package dev.booking.sports.shared.upload;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

/** Storage port. Only the R2 adapter knows about S3 SDK classes. */
public interface ObjectStorage {
    record PutGrant(URI url, Map<String, String> headers) {}
    record Metadata(long sizeBytes, String contentType, String etag, Map<String, String> metadata) {}
    PutGrant presignPut(String key, String contentType, String uploadId, Duration ttl);
    Metadata head(String key);
    byte[] readForValidation(String key, String etag, int maxBytes);
    void promote(String sourceKey, String destinationKey, String etag);
    URI presignGet(String key, Duration ttl);
    void delete(String key);
}
