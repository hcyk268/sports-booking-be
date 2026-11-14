package dev.booking.sports.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.notification.realtime")
public record NotificationRealtimeProperties(String channel, int publisherThreads, int publisherQueueCapacity,
        int subscriberThreads, int subscriberQueueCapacity) {
    public NotificationRealtimeProperties {
        if (channel == null || channel.isBlank() || publisherThreads < 1 || publisherQueueCapacity < 1
                || subscriberThreads < 1 || subscriberQueueCapacity < 1) {
            throw new IllegalArgumentException("Invalid notification realtime configuration");
        }
    }
}
