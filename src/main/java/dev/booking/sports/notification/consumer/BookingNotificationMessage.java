package dev.booking.sports.notification.consumer;

import java.util.UUID;

import tools.jackson.databind.JsonNode;

/** Local view of the versioned booking wire envelope, independent of producer infrastructure. */
public record BookingNotificationMessage(UUID eventId, String aggregateType, String aggregateId,
        String eventType, int eventVersion, JsonNode payload) {
}
