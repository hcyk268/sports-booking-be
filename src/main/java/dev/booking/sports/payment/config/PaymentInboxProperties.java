package dev.booking.sports.payment.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.payment.inbox")
public record PaymentInboxProperties(int batchSize, Duration leaseDuration, Duration retryDelay, int maxAttempts) {
    public PaymentInboxProperties {
        batchSize = batchSize == 0 ? 25 : batchSize;
        leaseDuration = leaseDuration == null ? Duration.ofSeconds(60) : leaseDuration;
        retryDelay = retryDelay == null ? Duration.ofSeconds(15) : retryDelay;
        maxAttempts = maxAttempts == 0 ? 8 : maxAttempts;
        if (batchSize < 1 || batchSize > 100 || maxAttempts < 1 || leaseDuration.compareTo(Duration.ofSeconds(1)) < 0
                || retryDelay.isNegative() || retryDelay.isZero()) throw new IllegalArgumentException("Invalid payment inbox configuration");
    }
}
