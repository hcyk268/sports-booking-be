package dev.booking.sports.payment.domain.event;

import java.time.Instant;
import java.util.UUID;

public record PaymentReviewRequiredEvent(UUID inboxId, UUID paymentId, UUID transactionId,
        String invoiceNumber, String reasonCode, Instant occurredAt) {}
