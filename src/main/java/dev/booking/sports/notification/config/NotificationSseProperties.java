package dev.booking.sports.notification.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.notification.sse")
public record NotificationSseProperties(Duration heartbeatInterval, Duration connectionTimeout, Duration sendTimeout,
        int maxConnectionsPerUser, int maxConnections, int connectionQueueCapacity, int senderThreads, int senderQueueCapacity) {
    public NotificationSseProperties {
        if (heartbeatInterval == null || heartbeatInterval.toMillis() < 1000
                || connectionTimeout == null || connectionTimeout.compareTo(heartbeatInterval) <= 0
                || sendTimeout == null || sendTimeout.toMillis() < 1000
                || maxConnectionsPerUser < 1 || maxConnections < maxConnectionsPerUser
                || connectionQueueCapacity < 1 || senderThreads < 1 || senderQueueCapacity < 1) {
            throw new IllegalArgumentException("Invalid notification SSE limits");
        }
    }
}
