package dev.booking.sports.shared.upload.r2;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import dev.booking.sports.shared.exception.ApiException;
import dev.booking.sports.shared.upload.UploadErrorCode;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.*;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class R2ObjectStorageTest {
    @Test void grantUsesOnlyStagingAndSignsMimeAndUploadIdentity() {
        try (var signer = S3Presigner.builder().region(Region.of("auto"))
                .endpointOverride(URI.create("https://test.r2.cloudflarestorage.com"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test-access", "test-secret")))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build()).build()) {
            var adapter = new R2ObjectStorage(mock(S3Client.class), signer, "test-bucket");
            var ticket = adapter.presignPut("staging/test-id", "image/png", "upload-id", Duration.ofMinutes(5));
            assertThat(ticket.url().getHost()).isEqualTo("test.r2.cloudflarestorage.com");
            assertThat(ticket.url().getPath()).isEqualTo("/test-bucket/staging/test-id");
            assertThat(ticket.headers()).containsEntry("content-type", "image/png");
            assertThat(ticket.url().toString()).contains("X-Amz-SignedHeaders=");
            assertThat(ticket.headers().keySet()).noneMatch(key -> key.equalsIgnoreCase("Authorization"));
            // Metadata may be signed as a header or hoisted into the signed query by the SDK.
            assertThat(ticket.url().toString().contains("upload-id") || ticket.headers().containsKey("x-amz-meta-upload-id")).isTrue();
        }
    }
    @Test void promotionIsConditionalAndPreconditionFailureIsSafeToRetry() {
        var client = mock(S3Client.class);
        var adapter = new R2ObjectStorage(client, mock(S3Presigner.class), "bucket");
        AtomicReference<CopyObjectRequest> request = new AtomicReference<>();
        when(client.copyObject(any(CopyObjectRequest.class))).thenAnswer(call -> {
            request.set(call.getArgument(0));
            throw S3Exception.builder().statusCode(412).message("changed").build();
        });
        assertThatThrownBy(() -> adapter.promote("staging/id", "assets/id.png", "\"etag\""))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(UploadErrorCode.UPLOAD_CHANGED));
        assertThat(request.get().copySourceIfMatch()).isEqualTo("\"etag\"");
        assertThat(request.get().key()).isEqualTo("assets/id.png");
    }
}
