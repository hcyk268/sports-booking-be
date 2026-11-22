package dev.booking.sports.shared.upload.s3;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@ConditionalOnProperty(prefix = "app.storage.s3", name = "enabled", havingValue = "true")
public class S3Configuration {
    private final Region region;
    private final String accessKey, secretKey, sessionToken;

    public S3Configuration(@Value("${app.storage.s3.region:}") String region,
            @Value("${app.storage.s3.bucket-name:}") String bucket,
            @Value("${app.storage.s3.access-key:}") String accessKey,
            @Value("${app.storage.s3.secret-key:}") String secretKey,
            @Value("${app.storage.s3.session-token:}") String sessionToken) {
        if (region.isBlank() || bucket.isBlank())
            throw new IllegalStateException("S3 is enabled but region or bucket is missing");
        if (accessKey.isBlank() != secretKey.isBlank()
                || (!sessionToken.isBlank() && accessKey.isBlank()))
            throw new IllegalStateException("S3 credentials require both access key and secret key");
        this.region = Region.of(region);
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.sessionToken = sessionToken;
    }

    @Bean
    AwsCredentialsProvider s3CredentialsProvider() {
        if (accessKey.isBlank()) return DefaultCredentialsProvider.builder().build();
        return StaticCredentialsProvider.create(sessionToken.isBlank()
                ? AwsBasicCredentials.create(accessKey, secretKey)
                : AwsSessionCredentials.create(accessKey, secretKey, sessionToken));
    }

    @Bean(destroyMethod = "close")
    S3Client s3Client() {
        return S3Client.builder().region(region).credentialsProvider(s3CredentialsProvider())
                .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(3))
                        .socketTimeout(Duration.ofSeconds(10)))
                .overrideConfiguration(builder -> builder.apiCallTimeout(Duration.ofSeconds(20))
                        .apiCallAttemptTimeout(Duration.ofSeconds(12)))
                .build();
    }

    @Bean(destroyMethod = "close")
    S3Presigner s3Presigner() {
        return S3Presigner.builder().region(region).credentialsProvider(s3CredentialsProvider()).build();
    }
}
