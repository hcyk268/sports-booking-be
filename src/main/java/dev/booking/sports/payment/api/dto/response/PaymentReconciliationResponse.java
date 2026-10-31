package dev.booking.sports.payment.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PaymentReconciliationResponse(UUID paymentId, UUID attemptId, Instant requestedAt) {
}
