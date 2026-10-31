package dev.booking.sports.payment.infrastructure.messaging;

import java.util.UUID;

import tools.jackson.databind.JsonNode;

public record PaymentEventMessage(UUID eventId, String aggregateType, String aggregateId,
        String eventType, int eventVersion, JsonNode payload) {}
