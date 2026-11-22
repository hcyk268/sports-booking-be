package dev.booking.sports.shared.upload.s3;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import dev.booking.sports.shared.upload.StorageProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Component
@ConditionalOnProperty(prefix = "app.storage.s3", name = "enabled", havingValue = "true")
public class S3ObjectStorage extends S3CompatibleObjectStorage {
    public S3ObjectStorage(@Qualifier("s3Client") S3Client client,
            @Qualifier("s3Presigner") S3Presigner presigner,
            @Value("${app.storage.s3.bucket-name}") String bucket) {
        super(StorageProvider.S3, client, presigner, bucket);
    }
}
