package dev.booking.sports.shared.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.outbox")
public record OutboxProperties(Duration pollInterval, int batchSize, int maxRetries) {
}
