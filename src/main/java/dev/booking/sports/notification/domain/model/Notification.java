package dev.booking.sports.notification.domain.model;

import java.time.Instant;
import java.util.UUID;

import org.springframework.util.Assert;

import dev.booking.sports.shared.persistence.CreatableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends CreatableEntity {

    @Column(name = "recipient_id", nullable = false, updatable = false)
    private UUID recipientId;

    @Column(nullable = false, length = 100, updatable = false)
    private String type;

    @Column(nullable = false, length = 255, updatable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "text", updatable = false)
    private String body;

    @Column(name = "resource_type", length = 50, updatable = false)
    private String resourceType;

    @Column(name = "resource_id", updatable = false)
    private UUID resourceId;

    @Column(name = "source_event_id", nullable = false, updatable = false)
    private UUID sourceEventId;

    @Column(name = "read_at")
    private Instant readAt;

    public static Notification create(UUID recipientId, String type, String title, String body,
            String resourceType, UUID resourceId, UUID sourceEventId) {
        Assert.notNull(recipientId, "Recipient ID is required");
        Assert.notNull(sourceEventId, "Source event ID is required");
        Assert.hasText(type, "Notification type is required");
        Assert.hasText(title, "Notification title is required");
        Assert.hasText(body, "Notification body is required");
        Assert.isTrue((resourceType == null) == (resourceId == null),
                "Resource type and ID must be provided together");
        if (resourceType != null) {
            Assert.hasText(resourceType, "Resource type must not be blank");
        }

        Notification notification = new Notification();
        notification.recipientId = recipientId;
        notification.type = type;
        notification.title = title;
        notification.body = body;
        notification.resourceType = resourceType;
        notification.resourceId = resourceId;
        notification.sourceEventId = sourceEventId;
        return notification;
    }

    public void markRead(Instant readAt) {
        Assert.notNull(readAt, "Read time is required");
        if (this.readAt == null) {
            this.readAt = readAt;
        }
    }
}
