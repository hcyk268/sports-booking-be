package dev.booking.sports.shared.upload;

import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Configuration @EnableScheduling @RequiredArgsConstructor @Slf4j
@ConditionalOnExpression("${app.storage.r2.enabled:false} or ${app.storage.s3.enabled:false}")
public class UploadCleanupSchedule {
    private final UploadAssetRepository repository;
    private final UploadCleanup cleanup;
    private final ObjectStorageRegistry storages;
    @Scheduled(fixedDelayString = "${app.storage.cleanup-interval:${app.storage.r2.cleanup-interval:5m}}")
    public void run() {
        var ids = repository.cleanupCandidates(UploadAsset.Status.DELETE_PENDING, List.of(UploadAsset.Status.PENDING, UploadAsset.Status.READY),
                Instant.now(), storages.enabledProviders(), PageRequest.of(0, 50));
        for (var id : ids) {
            try { cleanup.clean(id); }
            catch (RuntimeException ex) { log.warn("Upload cleanup will retry asset {}", id); }
        }
    }
}
