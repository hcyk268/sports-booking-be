package dev.booking.sports.shared.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.outbox")
public record OutboxProperties(Duration pollInterval, int batchSize, int maxRetries,
        Duration retryInitialDelay, Duration retryMaxDelay) {
    public OutboxProperties {
        retryInitialDelay = retryInitialDelay == null ? Duration.ofSeconds(5) : retryInitialDelay;
        retryMaxDelay = retryMaxDelay == null ? Duration.ofMinutes(5) : retryMaxDelay;
        if (retryInitialDelay.isNegative() || retryInitialDelay.toMillis() < 1
                || retryMaxDelay.compareTo(retryInitialDelay) < 0) {
            throw new IllegalArgumentException("Invalid outbox retry delays");
        }
    }

    public Duration retryDelay(int previousAttempts) {
        long initial = retryInitialDelay.toMillis();
        long max = retryMaxDelay.toMillis();
        long factor = 1L << Math.min(Math.max(previousAttempts, 0), 30);
        return Duration.ofMillis(initial > max / factor ? max : Math.min(initial * factor, max));
    }
}
