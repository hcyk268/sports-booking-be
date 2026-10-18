package dev.booking.sports.shared.upload.r2;

import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@ConditionalOnProperty(prefix = "app.storage.r2", name = "enabled", havingValue = "true")
public class R2Configuration {
    private final URI endpoint;
    private final StaticCredentialsProvider credentials;
    public R2Configuration(@Value("${app.storage.r2.endpoint:}") String endpoint,
            @Value("${app.storage.r2.access-key:}") String accessKey,
            @Value("${app.storage.r2.secret-key:}") String secretKey,
            @Value("${app.storage.r2.bucket-name:}") String bucket) {
        if (endpoint.isBlank() || accessKey.isBlank() || secretKey.isBlank() || bucket.isBlank())
            throw new IllegalStateException("R2 is enabled but endpoint, bucket or credentials are missing");
        this.endpoint = URI.create(endpoint);
        if (!"https".equals(this.endpoint.getScheme()) || this.endpoint.getHost() == null
                || !this.endpoint.getHost().endsWith(".r2.cloudflarestorage.com")
                || this.endpoint.getUserInfo() != null || this.endpoint.getQuery() != null
                || this.endpoint.getFragment() != null
                || (this.endpoint.getPath() != null && !this.endpoint.getPath().isEmpty() && !this.endpoint.getPath().equals("/")))
            throw new IllegalStateException("Use the HTTPS R2 S3 account endpoint, without a bucket path");
        credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }
    @Bean(destroyMethod = "close")
    S3Client r2Client() {
        return S3Client.builder().endpointOverride(endpoint).credentialsProvider(credentials).region(Region.of("auto"))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(3))
                        .socketTimeout(Duration.ofSeconds(10)))
                .overrideConfiguration(builder -> builder.apiCallTimeout(Duration.ofSeconds(20))
                        .apiCallAttemptTimeout(Duration.ofSeconds(12)))
                .build();
    }
    @Bean(destroyMethod = "close")
    S3Presigner r2Presigner() {
        return S3Presigner.builder().endpointOverride(endpoint).credentialsProvider(credentials).region(Region.of("auto"))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build()).build();
    }
}
