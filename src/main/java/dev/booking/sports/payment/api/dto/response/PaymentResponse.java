package dev.booking.sports.payment.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import dev.booking.sports.payment.domain.enums.PaymentStatus;

public record PaymentResponse(
        UUID id,
        UUID bookingId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        Instant expiresAt,
        UUID settledTransactionId,
        Instant settledAt,
        boolean canResumeCheckout,
        Long version,
        Instant createdAt,
        Instant updatedAt,
        List<PaymentAttemptResponse> attempts,
        List<PaymentTransactionResponse> transactions,
        List<PaymentRefundSummaryResponse> refunds) {
}
