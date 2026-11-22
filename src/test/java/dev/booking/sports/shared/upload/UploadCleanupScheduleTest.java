package dev.booking.sports.shared.upload;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UploadCleanupScheduleTest {
    @ParameterizedTest
    @CsvSource({"true,false", "false,true", "true,true", "false,false"})
    void cleanupRunsWhenEitherProviderIsEnabled(boolean r2Enabled, boolean s3Enabled) {
        new ApplicationContextRunner().withUserConfiguration(UploadCleanupSchedule.class)
                .withBean(UploadAssetRepository.class, () -> mock(UploadAssetRepository.class))
                .withBean(UploadCleanup.class, () -> mock(UploadCleanup.class))
                .withBean(ObjectStorageRegistry.class, () -> mock(ObjectStorageRegistry.class))
                .withPropertyValues("app.storage.r2.enabled=" + r2Enabled, "app.storage.s3.enabled=" + s3Enabled)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    if (r2Enabled || s3Enabled) assertThat(context).hasSingleBean(UploadCleanupSchedule.class);
                    else assertThat(context).doesNotHaveBean(UploadCleanupSchedule.class);
                });
    }
}
