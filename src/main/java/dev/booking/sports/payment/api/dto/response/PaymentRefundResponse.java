package dev.booking.sports.payment.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import dev.booking.sports.payment.domain.enums.PaymentRefundStatus;

public record PaymentRefundResponse(
        UUID id,
        UUID paymentId,
        UUID paymentTransactionId,
        BigDecimal amount,
        String currency,
        PaymentRefundStatus status,
        String reason,
        UUID requestedBy,
        UUID processedBy,
        Instant processingStartedAt,
        Instant completedAt,
        String refundReference,
        UUID evidenceAssetId,
        String adminNote,
        Long version,
        Instant createdAt,
        Instant updatedAt) {
    public static PaymentRefundResponse from(dev.booking.sports.payment.domain.model.PaymentRefund refund) {
        return new PaymentRefundResponse(refund.getId(), refund.getPaymentId(), refund.getPaymentTransactionId(),
                refund.getAmount(), refund.getCurrency(), refund.getStatus(), refund.getReason(),
                refund.getRequestedBy(), refund.getProcessedBy(), refund.getProcessingStartedAt(),
                refund.getCompletedAt(), refund.getRefundReference(), refund.getEvidenceAssetId(),
                refund.getAdminNote(), refund.getVersion(), refund.getCreatedAt(), refund.getUpdatedAt());
    }
}
