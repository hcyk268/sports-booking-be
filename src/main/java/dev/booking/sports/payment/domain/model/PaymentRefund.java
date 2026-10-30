package dev.booking.sports.payment.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import dev.booking.sports.payment.domain.enums.PaymentRefundStatus;
import dev.booking.sports.shared.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment_refunds")
@Getter
@NoArgsConstructor
public class PaymentRefund extends BaseEntity {
    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "payment_transaction_id", nullable = false, updatable = false)
    private UUID paymentTransactionId;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentRefundStatus status;

    @Column(nullable = false, length = 1000, updatable = false)
    private String reason;

    @Column(name = "idempotency_key", nullable = false, length = 100, updatable = false)
    private String idempotencyKey;

    @Column(name = "idempotency_request_hash", nullable = false, length = 64, updatable = false)
    private String idempotencyRequestHash;

    @Column(name = "requested_by", updatable = false)
    private UUID requestedBy;

    @Column(name = "processed_by")
    private UUID processedBy;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "refund_reference", length = 255)
    private String refundReference;

    @Column(name = "evidence_asset_id")
    private UUID evidenceAssetId;

    @Column(name = "admin_note", length = 2000)
    private String adminNote;

    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(mappedBy = "refund", cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC, id ASC")
    private List<PaymentRefundStatusHistory> statusHistory = new ArrayList<>();

    /** Application must lock the receipt and check the total reserved refund budget. */
    public static PaymentRefund request(PaymentTransaction source, BigDecimal amount, String reason,
            String idempotencyKey, String requestHash, UUID requestedBy) {
        Objects.requireNonNull(source, "source transaction must not be null");
        PaymentValidation.vndAmount(amount);
        if (!source.isApprovedPayment() || source.getPaymentId() == null
                || amount.compareTo(source.getAmount()) > 0) {
            throw new IllegalStateException("Refund needs a matched collection with sufficient funds");
        }
        PaymentRefund refund = new PaymentRefund();
        refund.paymentId = source.getPaymentId();
        refund.paymentTransactionId = Objects.requireNonNull(source.getId(), "Receipt must be persisted first");
        refund.amount = amount;
        refund.currency = PaymentValidation.vnd(source.getCurrency());
        refund.reason = PaymentValidation.text(reason, "reason", 1000);
        refund.idempotencyKey = PaymentValidation.text(idempotencyKey, "idempotencyKey", 100);
        refund.idempotencyRequestHash = PaymentValidation.hash(requestHash);
        refund.requestedBy = requestedBy;
        refund.status = PaymentRefundStatus.REQUESTED;
        refund.statusHistory.add(PaymentRefundStatusHistory.record(
                refund, null, refund.status, requestedBy, reason));
        return refund;
    }

    public List<PaymentRefundStatusHistory> getStatusHistory() {
        return List.copyOf(statusHistory);
    }

    public boolean hasIdempotencyRequestHash(String hash) {
        return idempotencyRequestHash.equals(hash);
    }

    public boolean reservesFunds() {
        return status.reservesFunds();
    }

    public void startProcessing(UUID adminId, Instant now) {
        Objects.requireNonNull(adminId, "adminId must not be null");
        Objects.requireNonNull(now, "now must not be null");
        requireStatus(PaymentRefundStatus.REQUESTED);
        processedBy = adminId;
        processingStartedAt = now;
        transition(PaymentRefundStatus.PROCESSING, adminId, null);
    }

    public void complete(UUID adminId, String reference, UUID evidenceAssetId, String note, Instant now) {
        Objects.requireNonNull(adminId, "adminId must not be null");
        Objects.requireNonNull(now, "now must not be null");
        PaymentValidation.text(reference, "refund reference", 255);
        PaymentValidation.optionalText(note, "admin note", 2000);
        requireStatus(PaymentRefundStatus.PROCESSING);
        if (now.isBefore(processingStartedAt)) {
            throw new IllegalArgumentException("Completion cannot precede processing");
        }
        processedBy = adminId;
        refundReference = reference;
        this.evidenceAssetId = evidenceAssetId;
        adminNote = note;
        completedAt = now;
        transition(PaymentRefundStatus.COMPLETED, adminId, null);
    }

    public void reject(UUID adminId, String reason) {
        Objects.requireNonNull(adminId, "adminId must not be null");
        PaymentValidation.text(reason, "rejection reason", 1000);
        requireStatus(PaymentRefundStatus.REQUESTED);
        transition(PaymentRefundStatus.REJECTED, adminId, reason);
    }

    public void cancel(UUID actorId, String reason) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        PaymentValidation.text(reason, "cancellation reason", 1000);
        requireStatus(PaymentRefundStatus.REQUESTED);
        transition(PaymentRefundStatus.CANCELLED, actorId, reason);
    }

    private void requireStatus(PaymentRefundStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Refund must be " + expected + ", was " + status);
        }
    }

    private void transition(PaymentRefundStatus target, UUID actorId, String reason) {
        statusHistory.add(PaymentRefundStatusHistory.record(this, status, target, actorId, reason));
        status = target;
    }
}
