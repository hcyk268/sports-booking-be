package dev.booking.sports.shared.upload;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import dev.booking.sports.shared.exception.ApiException;
import dev.booking.sports.shared.upload.r2.R2Configuration;
import dev.booking.sports.shared.upload.r2.R2ObjectStorage;
import dev.booking.sports.shared.upload.s3.S3Configuration;
import dev.booking.sports.shared.upload.s3.S3ObjectStorage;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import static org.assertj.core.api.Assertions.*;

class ObjectStorageConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(R2Configuration.class, R2ObjectStorage.class,
                    S3Configuration.class, S3ObjectStorage.class, ObjectStorageRegistry.class);

    private ApplicationContextRunner r2() {
        return runner.withPropertyValues("app.storage.r2.enabled=true",
                "app.storage.r2.endpoint=https://test.r2.cloudflarestorage.com",
                "app.storage.r2.bucket-name=r2-bucket", "app.storage.r2.access-key=test-r2-access",
                "app.storage.r2.secret-key=test-r2-secret");
    }

    private String[] s3Properties() {
        return new String[] {"app.storage.s3.enabled=true", "app.storage.s3.region=ap-southeast-1",
                "app.storage.s3.bucket-name=s3-bucket", "app.storage.s3.access-key=test-s3-access",
                "app.storage.s3.secret-key=test-s3-secret"};
    }

    @Test void disabledProvidersKeepTheAppAvailableAndUploadsUnavailable() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(ObjectStorage.class);
            var registry = context.getBean(ObjectStorageRegistry.class);
            assertThat(registry.enabledProviders()).isEmpty();
            assertThatThrownBy(registry::forUploads).isInstanceOfSatisfying(ApiException.class,
                    ex -> assertThat(ex.getErrorCode()).isEqualTo(UploadErrorCode.UPLOAD_UNAVAILABLE));
        });
    }

    @Test void existingR2ConfigurationWorksWithoutNewProperties() {
        r2().run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(ObjectStorage.class);
            assertThat(context.getBean(ObjectStorageRegistry.class).forUploads()).isInstanceOf(R2ObjectStorage.class);
        });
    }

    @Test void s3OnlyConfigurationSelectsS3() {
        runner.withPropertyValues(s3Properties()).withPropertyValues("app.storage.provider=s3").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(ObjectStorage.class);
            var registry = context.getBean(ObjectStorageRegistry.class);
            assertThat(registry.forUploads()).isInstanceOf(S3ObjectStorage.class);
            assertThat(registry.enabledProviders()).containsExactly(StorageProvider.S3);
            assertThatThrownBy(() -> registry.forProvider(StorageProvider.R2)).isInstanceOf(ApiException.class);
        });
    }

    @Test void bothProvidersUseTheirOwnClientsAndSigningCredentials() {
        r2().withPropertyValues(s3Properties()).withPropertyValues("app.storage.provider=S3").run(context -> {
            assertThat(context).hasNotFailed();
            var registry = context.getBean(ObjectStorageRegistry.class);
            assertThat(registry.enabledProviders()).containsExactlyInAnyOrder(StorageProvider.R2, StorageProvider.S3);
            assertThat(registry.forUploads()).isSameAs(registry.forProvider(StorageProvider.S3));
            var r2Url = registry.forProvider(StorageProvider.R2).presignPut("staging/id", "image/png", "id", java.time.Duration.ofMinutes(5)).url();
            var s3Url = registry.forUploads().presignPut("staging/id", "image/png", "id", java.time.Duration.ofMinutes(5)).url();
            assertThat(r2Url.getHost()).isEqualTo("test.r2.cloudflarestorage.com");
            assertThat(r2Url.getRawQuery()).contains("test-r2-access");
            assertThat(s3Url.getHost()).isEqualTo("s3-bucket.s3.ap-southeast-1.amazonaws.com");
            assertThat(s3Url.getRawQuery()).contains("test-s3-access");
        });
    }

    @Test void selectedDisabledProviderFailsAtStartup() {
        r2().withPropertyValues("app.storage.provider=s3").run(context ->
                assertThat(context).hasFailed().getFailure().hasRootCauseMessage("Selected upload storage provider is not enabled: s3"));
    }

    @Test void invalidProviderFailsAtStartup() {
        runner.withPropertyValues("app.storage.provider=unknown").run(context -> assertThat(context).hasFailed());
    }

    @Test void missingBucketOrRegionFailsAtStartup() {
        runner.withPropertyValues(s3Properties()).withPropertyValues("app.storage.s3.bucket-name=")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues(s3Properties()).withPropertyValues("app.storage.s3.region=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test void partialCredentialsFailAtStartup() {
        runner.withPropertyValues(s3Properties()).withPropertyValues("app.storage.s3.secret-key=")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues(s3Properties()).withPropertyValues("app.storage.s3.access-key=", "app.storage.s3.secret-key=", "app.storage.s3.session-token=token")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test void emptyCredentialsUseTheDefaultAwsChainWithoutResolvingOnStartup() {
        runner.withPropertyValues(s3Properties()).withPropertyValues("app.storage.provider=s3",
                "app.storage.s3.access-key=", "app.storage.s3.secret-key=").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean("s3CredentialsProvider")).isInstanceOf(DefaultCredentialsProvider.class);
        });
    }

    @Test void temporaryCredentialsRetainSessionToken() {
        runner.withPropertyValues(s3Properties()).withPropertyValues("app.storage.provider=s3",
                "app.storage.s3.session-token=test-session").run(context -> {
            assertThat(context).hasNotFailed();
            var credentials = context.getBean("s3CredentialsProvider", AwsCredentialsProvider.class).resolveCredentials();
            assertThat(credentials).isInstanceOfSatisfying(AwsSessionCredentials.class,
                    session -> assertThat(session.sessionToken()).isEqualTo("test-session"));
        });
    }
}
