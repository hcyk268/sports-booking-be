package dev.booking.sports.shared.upload.r2;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import dev.booking.sports.shared.upload.StorageProvider;
import dev.booking.sports.shared.upload.s3.S3CompatibleObjectStorage;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Component
@ConditionalOnProperty(prefix = "app.storage.r2", name = "enabled", havingValue = "true")
public class R2ObjectStorage extends S3CompatibleObjectStorage {
    public R2ObjectStorage(@Qualifier("r2Client") S3Client client,
            @Qualifier("r2Presigner") S3Presigner presigner,
            @Value("${app.storage.r2.bucket-name}") String bucket) {
        super(StorageProvider.R2, client, presigner, bucket);
    }
}
