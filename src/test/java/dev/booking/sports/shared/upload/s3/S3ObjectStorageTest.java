package dev.booking.sports.shared.upload.s3;

import java.time.Duration;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import dev.booking.sports.shared.exception.ApiException;
import dev.booking.sports.shared.upload.UploadErrorCode;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class S3ObjectStorageTest {
    @Test void signsRegionalPrivateUploadAndReadWithTemporaryCredentials() {
        try (var signer = S3Presigner.builder().region(Region.AP_SOUTHEAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsSessionCredentials.create("test-access", "test-secret", "test-token"))).build()) {
            var adapter = new S3ObjectStorage(mock(S3Client.class), signer, "test-bucket");
            var grant = adapter.presignPut("staging/id", "image/png", "upload-id", Duration.ofMinutes(5));
            assertThat(grant.url().getHost()).isEqualTo("test-bucket.s3.ap-southeast-1.amazonaws.com");
            assertThat(grant.url().getPath()).isEqualTo("/staging/id");
            assertThat(grant.url().getRawQuery()).contains("X-Amz-Expires=300", "ap-southeast-1", "X-Amz-Security-Token=test-token");
            assertThat(grant.headers()).containsEntry("content-type", "image/png");
            assertThat(grant.headers().keySet()).noneMatch(key -> key.equalsIgnoreCase("host") || key.equalsIgnoreCase("authorization"));
            assertThat(grant.url().toString().contains("upload-id") || grant.headers().containsKey("x-amz-meta-upload-id")).isTrue();
            var read = adapter.presignGet("assets/id.png", Duration.ofSeconds(60));
            assertThat(read.getHost()).isEqualTo(grant.url().getHost());
            assertThat(read.getPath()).isEqualTo("/assets/id.png");
            assertThat(read.getRawQuery()).contains("X-Amz-Expires=60", "response-cache-control=private%2C%20no-store");
        }
    }

    @Test void validationUsesConditionalReadAndStopsOversizedObjects() {
        var client = mock(S3Client.class);
        var adapter = new S3ObjectStorage(client, mock(S3Presigner.class), "bucket");
        when(client.getObject(any(GetObjectRequest.class))).thenAnswer(call -> {
            GetObjectRequest request = call.getArgument(0);
            assertThat(request.ifMatch()).isEqualTo("etag");
            assertThat(request.bucket()).isEqualTo("bucket");
            return new ResponseInputStream<>(GetObjectResponse.builder().build(), new ByteArrayInputStream(new byte[11]));
        });
        assertThatThrownBy(() -> adapter.readForValidation("staging/id", "etag", 10))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(UploadErrorCode.UPLOAD_TOO_LARGE));
    }

    @Test void promotionAndDeletionUseTheConfiguredBucket() {
        var client = mock(S3Client.class);
        var adapter = new S3ObjectStorage(client, mock(S3Presigner.class), "bucket");
        adapter.promote("staging/id", "assets/id.png", "etag");
        verify(client).copyObject(CopyObjectRequest.builder().bucket("bucket").key("assets/id.png")
                .copySource("bucket/staging/id").copySourceIfMatch("etag").build());
        adapter.delete("staging/id");
        verify(client).deleteObject(DeleteObjectRequest.builder().bucket("bucket").key("staging/id").build());
    }

    @Test void sdkFailuresDoNotExposeCredentialOrRequestDetails() {
        var client = mock(S3Client.class);
        var signer = mock(S3Presigner.class);
        var adapter = new S3ObjectStorage(client, signer, "bucket");
        when(signer.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenThrow(SdkClientException.create("sensitive request details"));
        assertThatThrownBy(() -> adapter.presignPut("staging/id", "image/png", "id", Duration.ofMinutes(5)))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(UploadErrorCode.UPLOAD_UNAVAILABLE))
                .hasMessageNotContaining("sensitive");
        when(client.headObject(any(HeadObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(404).build());
        assertThatThrownBy(() -> adapter.head("staging/id"))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(UploadErrorCode.UPLOAD_NOT_READY));
    }
}
