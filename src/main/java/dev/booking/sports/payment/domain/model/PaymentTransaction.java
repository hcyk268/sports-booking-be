package dev.booking.sports.payment.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import dev.booking.sports.payment.domain.enums.*;
import dev.booking.sports.shared.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment_transactions")
@Getter
@NoArgsConstructor
public class PaymentTransaction extends BaseEntity {
    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "payment_attempt_id")
    private UUID paymentAttemptId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private PaymentEnvironment environment;

    @Column(name = "merchant_id", nullable = false, length = 100, updatable = false)
    private String merchantId;

    @Column(name = "provider_transaction_id", nullable = false, length = 150, updatable = false)
    private String providerTransactionId;

    @Column(name = "provider_transaction_reference", length = 150, updatable = false)
    private String providerTransactionReference;

    @Column(name = "provider_order_id", length = 150, updatable = false)
    private String providerOrderId;

    @Column(name = "invoice_number", length = 100, updatable = false)
    private String invoiceNumber;

    @Column(name = "transaction_type", nullable = false, length = 50, updatable = false)
    private String transactionType;

    @Column(name = "provider_status", nullable = false, length = 100)
    private String providerStatus;

    @Column(name = "payment_method", length = 50, updatable = false)
    private String paymentMethod;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "occurred_at", updatable = false)
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_status", nullable = false, length = 30)
    private PaymentResolutionStatus resolutionStatus;

    @Column(name = "resolution_reason", length = 1000)
    private String resolutionReason;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public static PaymentTransaction record(Receipt receipt, Instant receivedAt) {
        Objects.requireNonNull(receipt, "receipt must not be null");
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.provider = PaymentProvider.SEPAY;
        transaction.environment = receipt.environment();
        transaction.merchantId = receipt.merchantId();
        transaction.providerTransactionId = receipt.transactionId();
        transaction.providerTransactionReference = receipt.reference();
        transaction.providerOrderId = receipt.orderId();
        transaction.invoiceNumber = receipt.invoiceNumber();
        transaction.transactionType = receipt.type();
        transaction.providerStatus = receipt.status();
        transaction.paymentMethod = receipt.method();
        transaction.amount = receipt.amount();
        transaction.currency = receipt.currency();
        transaction.occurredAt = receipt.occurredAt();
        transaction.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        transaction.resolutionStatus = PaymentResolutionStatus.PENDING;
        return transaction;
    }

    public void match(PaymentAttempt attempt) {
        Objects.requireNonNull(attempt, "attempt must not be null");
        UUID attemptId = Objects.requireNonNull(attempt.getId(), "Attempt must be persisted first");
        if (provider != attempt.getProvider() || environment != attempt.getEnvironment()
                || !merchantId.equals(attempt.getMerchantId())
                || (invoiceNumber != null && !invoiceNumber.equals(attempt.getInvoiceNumber()))
                || (providerOrderId != null && attempt.getProviderOrderId() != null
                    && !providerOrderId.equals(attempt.getProviderOrderId()))) {
            throw new IllegalArgumentException("Receipt does not belong to this gateway attempt");
        }
        if (invoiceNumber == null && (providerOrderId == null
                || !providerOrderId.equals(attempt.getProviderOrderId()))) {
            throw new IllegalArgumentException("A matching invoice or gateway order is required");
        }
        if (paymentAttemptId != null && !paymentAttemptId.equals(attemptId)) {
            throw new IllegalStateException("Receipt cannot be reassigned to another attempt");
        }
        paymentId = attempt.getPaymentId();
        paymentAttemptId = attemptId;
    }

    public boolean isApprovedPayment() {
        return "PAYMENT".equals(transactionType) && "APPROVED".equals(providerStatus);
    }

    void requireApplicableTo(Payment payment) {
        if (payment.getId() == null || !payment.getId().equals(paymentId) || paymentAttemptId == null
                || !isApprovedPayment() || !currency.equals(payment.getCurrency())
                || amount.compareTo(payment.getAmount()) != 0
                || resolutionStatus != PaymentResolutionStatus.PENDING) {
            throw new IllegalStateException("Receipt is not eligible to settle this payment");
        }
    }

    void markApplied() {
        resolutionStatus = PaymentResolutionStatus.APPLIED;
    }

    public void requireReview(String reason) {
        String validatedReason = PaymentValidation.text(reason, "reason", 1000);
        resolutionStatus = PaymentResolutionStatus.REVIEW_REQUIRED;
        resolutionReason = validatedReason;
        resolvedBy = null;
        resolvedAt = null;
    }

    /** Call after authoritative reconciliation; a late paid IPN must not undo a void. */
    public void recordProviderStatus(String newStatus) {
        String validatedStatus = PaymentValidation.text(newStatus, "providerStatus", 100);
        if (!providerStatus.equals(validatedStatus) && resolutionStatus == PaymentResolutionStatus.APPLIED) {
            requireReview("Gateway status changed after settlement");
        }
        providerStatus = validatedStatus;
    }

    public void resolve(UUID actorId, String reason, Instant now) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(now, "now must not be null");
        PaymentValidation.text(reason, "reason", 1000);
        if (resolutionStatus != PaymentResolutionStatus.REVIEW_REQUIRED) {
            throw new IllegalStateException("Only a receipt requiring review can be resolved");
        }
        resolutionStatus = PaymentResolutionStatus.RESOLVED;
        resolutionReason = reason;
        resolvedBy = actorId;
        resolvedAt = now;
    }

    public record Receipt(PaymentEnvironment environment, String merchantId, String transactionId,
            String reference, String orderId, String invoiceNumber, String type, String status,
            String method, BigDecimal amount, String currency, Instant occurredAt) {
        public Receipt {
            Objects.requireNonNull(environment, "environment must not be null");
            PaymentValidation.text(merchantId, "merchantId", 100);
            PaymentValidation.text(transactionId, "transactionId", 150);
            PaymentValidation.optionalText(reference, "reference", 150);
            PaymentValidation.optionalText(orderId, "orderId", 150);
            PaymentValidation.optionalText(invoiceNumber, "invoiceNumber", 100);
            PaymentValidation.text(type, "transactionType", 50);
            PaymentValidation.text(status, "providerStatus", 100);
            PaymentValidation.optionalText(method, "paymentMethod", 50);
            PaymentValidation.amount(amount);
            PaymentValidation.currency(currency);
        }
    }
}
