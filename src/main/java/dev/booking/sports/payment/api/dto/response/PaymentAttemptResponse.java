package dev.booking.sports.payment.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import dev.booking.sports.payment.domain.enums.PaymentAttemptStatus;
import dev.booking.sports.payment.domain.enums.PaymentProvider;
import dev.booking.sports.payment.domain.model.PaymentAttempt;

public record PaymentAttemptResponse(
        UUID id,
        int attemptNumber,
        PaymentProvider provider,
        String invoiceNumber,
        String providerOrderId,
        String providerOrderStatus,
        String paymentMethod,
        BigDecimal amount,
        String currency,
        PaymentAttemptStatus status,
        Instant expiresAt,
        Instant lastReconciledAt,
        Instant nextReconcileAt,
        Long version,
        Instant createdAt) {
    public static PaymentAttemptResponse from(PaymentAttempt attempt) {
        return new PaymentAttemptResponse(attempt.getId(), attempt.getAttemptNumber(), attempt.getProvider(),
                attempt.getInvoiceNumber(), attempt.getProviderOrderId(), attempt.getProviderOrderStatus(),
                attempt.getPaymentMethod(), attempt.getAmount(), attempt.getCurrency(), attempt.getStatus(),
                attempt.getExpiresAt(), attempt.getLastReconciledAt(), attempt.getNextReconcileAt(),
                attempt.getVersion(), attempt.getCreatedAt());
    }
}
