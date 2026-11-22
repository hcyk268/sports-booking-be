package dev.booking.sports.shared.upload.s3;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import dev.booking.sports.shared.exception.ApiException;
import dev.booking.sports.shared.upload.ObjectStorage;
import dev.booking.sports.shared.upload.StorageProvider;
import dev.booking.sports.shared.upload.UploadErrorCode;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.*;

public class S3CompatibleObjectStorage implements ObjectStorage {
    private final StorageProvider provider;
    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;
    public S3CompatibleObjectStorage(StorageProvider provider, S3Client client, S3Presigner presigner, String bucket) {
        this.provider = provider;
        this.client = client; this.presigner = presigner; this.bucket = bucket;
    }
    public StorageProvider provider() { return provider; }
    public PutGrant presignPut(String key, String type, String uploadId, Duration ttl) {
        try {
            var request = PutObjectRequest.builder().bucket(bucket).key(key).contentType(type)
                    .metadata(Map.of("upload-id", uploadId)).build();
            var signed = presigner.presignPutObject(PutObjectPresignRequest.builder()
                    .signatureDuration(ttl).putObjectRequest(request).build());
            Map<String, String> headers = new HashMap<>();
            signed.signedHeaders().forEach((name, values) -> {
                if (!name.equalsIgnoreCase("host")) headers.put(name, String.join(",", values));
            });
            headers.put("content-type", type);
            return new PutGrant(URI.create(signed.url().toString()), Map.copyOf(headers));
        } catch (SdkException ex) { throw translate(ex); }
    }
    public Metadata head(String key) {
        try {
            var head = client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return new Metadata(head.contentLength(), head.contentType(), head.eTag(), head.metadata());
        } catch (SdkException ex) { throw translate(ex); }
    }
    public byte[] readForValidation(String key, String etag, int maxBytes) {
        try (var stream = client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).ifMatch(etag).build())) {
            byte[] bytes = stream.readNBytes(maxBytes + 1);
            if (bytes.length > maxBytes) throw new ApiException(UploadErrorCode.UPLOAD_TOO_LARGE);
            return bytes;
        } catch (IOException ex) { throw new ApiException(UploadErrorCode.UPLOAD_UNAVAILABLE); }
        catch (SdkException ex) { throw translate(ex); }
    }
    public void promote(String source, String destination, String etag) {
        try {
            client.copyObject(CopyObjectRequest.builder().bucket(bucket).key(destination)
                    .copySource(bucket + "/" + source).copySourceIfMatch(etag).build());
        } catch (SdkException ex) { throw translate(ex); }
    }
    public URI presignGet(String key, Duration ttl) {
        try {
            var request = GetObjectRequest.builder().bucket(bucket).key(key)
                    .responseCacheControl("private, no-store").build();
            return URI.create(presigner.presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(ttl).getObjectRequest(request).build()).url().toString());
        } catch (SdkException ex) { throw translate(ex); }
    }
    public void delete(String key) {
        try { client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build()); }
        catch (SdkException ex) { throw translate(ex); }
    }
    private ApiException translate(SdkException ex) {
        if (ex instanceof S3Exception s3) {
            if (s3.statusCode() == 404) return new ApiException(UploadErrorCode.UPLOAD_NOT_READY);
            if (s3.statusCode() == 412) return new ApiException(UploadErrorCode.UPLOAD_CHANGED);
        }
        // Do not expose the SDK message: it may contain request details or signed URLs.
        return new ApiException(UploadErrorCode.UPLOAD_UNAVAILABLE);
    }
}
