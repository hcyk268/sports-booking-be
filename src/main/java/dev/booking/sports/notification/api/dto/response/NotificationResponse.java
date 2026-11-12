package dev.booking.sports.notification.api.dto.response;

import java.time.Instant;
import java.util.UUID;

import dev.booking.sports.notification.domain.model.Notification;

public record NotificationResponse(
        UUID id,
        String type,
        String title,
        String body,
        String resourceType,
        UUID resourceId,
        Instant createdAt,
        Instant readAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(), notification.getType(), notification.getTitle(), notification.getBody(),
                notification.getResourceType(), notification.getResourceId(), notification.getCreatedAt(),
                notification.getReadAt());
    }
}
