package dev.booking.sports.payment.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import dev.booking.sports.payment.domain.enums.PaymentRefundStatus;
import dev.booking.sports.payment.domain.model.PaymentRefund;

public record PaymentRefundSummaryResponse(
        UUID id,
        UUID paymentTransactionId,
        BigDecimal amount,
        String currency,
        PaymentRefundStatus status,
        Instant completedAt,
        Instant createdAt) {
    public static PaymentRefundSummaryResponse from(PaymentRefund refund) {
        return new PaymentRefundSummaryResponse(refund.getId(), refund.getPaymentTransactionId(),
                refund.getAmount(), refund.getCurrency(), refund.getStatus(), refund.getCompletedAt(), refund.getCreatedAt());
    }
}
