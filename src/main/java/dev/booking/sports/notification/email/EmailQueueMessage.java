package dev.booking.sports.notification.email;

import java.util.UUID;

public record EmailQueueMessage(UUID outboxEventId, EmailMessage email) {
}
