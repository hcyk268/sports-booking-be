package dev.booking.sports.payment.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import dev.booking.sports.payment.domain.enums.PaymentProvider;
import dev.booking.sports.payment.domain.enums.PaymentResolutionStatus;
import dev.booking.sports.payment.domain.model.PaymentTransaction;

public record PaymentTransactionResponse(
        UUID id,
        UUID paymentId,
        UUID paymentAttemptId,
        PaymentProvider provider,
        String providerTransactionId,
        String providerTransactionReference,
        String transactionType,
        String providerStatus,
        String paymentMethod,
        BigDecimal amount,
        String currency,
        Instant occurredAt,
        Instant receivedAt,
        PaymentResolutionStatus resolutionStatus,
        String resolutionReason,
        UUID resolvedBy,
        Instant resolvedAt,
        Long version) {
    public static PaymentTransactionResponse from(PaymentTransaction transaction) {
        return new PaymentTransactionResponse(transaction.getId(), transaction.getPaymentId(),
                transaction.getPaymentAttemptId(), transaction.getProvider(), transaction.getProviderTransactionId(),
                transaction.getProviderTransactionReference(), transaction.getTransactionType(), transaction.getProviderStatus(),
                transaction.getPaymentMethod(), transaction.getAmount(), transaction.getCurrency(),
                transaction.getOccurredAt(), transaction.getReceivedAt(), transaction.getResolutionStatus(),
                transaction.getResolutionReason(), transaction.getResolvedBy(), transaction.getResolvedAt(), transaction.getVersion());
    }
}
