package dev.booking.sports.booking.infrastructure.messaging;

import java.util.Objects;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

public record BookingEventMessage(
        UUID eventId,
        String aggregateType,
        String aggregateId,
        String eventType,
        int eventVersion,
        JsonNode payload) {

    public BookingEventMessage {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(aggregateType, "aggregateType must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(payload, "payload must not be null");

        if (eventVersion < 1) {
            throw new IllegalArgumentException("eventVersion must be positive");
        }
    }
}
