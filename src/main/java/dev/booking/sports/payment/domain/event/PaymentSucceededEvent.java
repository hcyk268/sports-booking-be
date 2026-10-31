package dev.booking.sports.payment.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentSucceededEvent(UUID paymentId, UUID bookingId, UUID attemptId, UUID transactionId,
        BigDecimal amount, String currency, Instant occurredAt) {}
