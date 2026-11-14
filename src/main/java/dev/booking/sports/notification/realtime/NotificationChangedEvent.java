package dev.booking.sports.notification.realtime;

import java.util.Objects;
import java.util.UUID;

public record NotificationChangedEvent(UUID eventId, UUID recipientId, Action action, UUID notificationId) {
    public enum Action { CREATED, READ, READ_ALL }

    public NotificationChangedEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(recipientId, "recipientId");
        Objects.requireNonNull(action, "action");
        if (action == Action.READ_ALL && notificationId != null
                || action != Action.READ_ALL && notificationId == null) {
            throw new IllegalArgumentException("notificationId is required only for CREATED and READ");
        }
    }

    public Payload payload() {
        return new Payload(eventId, action, notificationId);
    }
    public record Payload(UUID eventId, Action action, UUID notificationId) {}
}
