package dev.booking.sports.payment.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import dev.booking.sports.payment.domain.enums.*;
import dev.booking.sports.shared.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment_attempts")
@Getter
@NoArgsConstructor
public class PaymentAttempt extends BaseEntity {
    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "attempt_number", nullable = false, updatable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private PaymentEnvironment environment;

    @Column(name = "merchant_id", nullable = false, length = 100, updatable = false)
    private String merchantId;

    @Column(name = "invoice_number", nullable = false, length = 100, updatable = false)
    private String invoiceNumber;

    @Column(name = "provider_order_id", length = 150)
    private String providerOrderId;

    @Column(name = "provider_order_status", length = 100)
    private String providerOrderStatus;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentAttemptStatus status;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "checkout_payload", nullable = false, columnDefinition = "jsonb", updatable = false)
    private List<CheckoutField> checkoutPayload;

    @Column(name = "idempotency_key", nullable = false, length = 100, updatable = false)
    private String idempotencyKey;

    @Column(name = "idempotency_request_hash", nullable = false, length = 64, updatable = false)
    private String idempotencyRequestHash;

    @Column(name = "last_reconciled_at")
    private Instant lastReconciledAt;

    @Column(name = "next_reconcile_at")
    private Instant nextReconcileAt;

    @Column(name = "reconciliation_attempts", nullable = false)
    private int reconciliationAttempts;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Version
    @Column(nullable = false)
    private Long version;

    public static PaymentAttempt prepare(Payment payment, int attemptNumber, CheckoutSpec spec, Instant now) {
        Objects.requireNonNull(payment, "payment must not be null").requireCheckoutAllowed(now);
        Objects.requireNonNull(spec, "checkout spec must not be null");
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be positive");
        }
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.paymentId = Objects.requireNonNull(payment.getId(), "Payment must be persisted first");
        attempt.attemptNumber = attemptNumber;
        attempt.provider = PaymentProvider.SEPAY;
        attempt.environment = spec.environment();
        attempt.merchantId = spec.merchantId();
        attempt.invoiceNumber = spec.invoiceNumber();
        attempt.paymentMethod = spec.paymentMethod();
        attempt.amount = payment.getAmount();
        attempt.currency = payment.getCurrency();
        attempt.expiresAt = payment.getExpiresAt();
        attempt.status = PaymentAttemptStatus.PREPARED;
        attempt.checkoutPayload = spec.fields();
        attempt.idempotencyKey = spec.idempotencyKey();
        attempt.idempotencyRequestHash = spec.requestHash();
        return attempt;
    }

    public List<CheckoutField> getCheckoutPayload() {
        return List.copyOf(checkoutPayload);
    }

    public boolean hasIdempotencyRequestHash(String hash) {
        return idempotencyRequestHash.equals(hash);
    }

    public boolean canResumeCheckout(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return (status == PaymentAttemptStatus.PREPARED || status == PaymentAttemptStatus.PENDING)
                && now.isBefore(expiresAt);
    }

    public void markPending(String orderId, String orderStatus) {
        if (!status.isActive()) {
            throw new IllegalStateException("Terminal attempt cannot become pending");
        }
        bindGatewayOrder(orderId, orderStatus);
        status = PaymentAttemptStatus.PENDING;
    }

    public void markUnknown(String error, Instant nextCheckAt) {
        if (!status.isActive()) {
            throw new IllegalStateException("Only an active attempt can become unknown");
        }
        String validatedError = PaymentValidation.text(error, "error", 2000);
        Instant validatedNextCheck = Objects.requireNonNull(nextCheckAt, "nextCheckAt must not be null");
        status = PaymentAttemptStatus.UNKNOWN;
        lastError = validatedError;
        nextReconcileAt = validatedNextCheck;
    }

    public void recordReconciliation(Instant checkedAt, Instant nextCheckAt, String error) {
        Objects.requireNonNull(checkedAt, "checkedAt must not be null");
        if (lastReconciledAt != null && checkedAt.isBefore(lastReconciledAt)) {
            throw new IllegalArgumentException("Reconciliation time cannot move backwards");
        }
        if (nextCheckAt != null && !nextCheckAt.isAfter(checkedAt)) {
            throw new IllegalArgumentException("Next reconciliation must be in the future");
        }
        String validatedError = PaymentValidation.optionalText(error, "error", 2000);
        lastReconciledAt = checkedAt;
        nextReconcileAt = nextCheckAt;
        lastError = validatedError;
        reconciliationAttempts++;
    }

    public void expire(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (status == PaymentAttemptStatus.EXPIRED) {
            return;
        }
        if (!status.isActive() || now.isBefore(expiresAt)) {
            throw new IllegalStateException("Attempt cannot expire in its current state/time");
        }
        status = PaymentAttemptStatus.EXPIRED;
    }

    public void recordGatewayOutcome(PaymentAttemptStatus outcome, String orderId, String orderStatus) {
        Objects.requireNonNull(outcome, "outcome must not be null");
        if (outcome.isActive() || outcome == PaymentAttemptStatus.EXPIRED) {
            throw new IllegalArgumentException("Expected a verified success, failure or cancellation");
        }
        if (status == PaymentAttemptStatus.SUCCEEDED && outcome != PaymentAttemptStatus.SUCCEEDED) {
            throw new IllegalStateException("A collected payment needs separate reversal handling");
        }
        // Even a locally expired/cancelled attempt may have actually collected money.
        if (!status.isActive() && outcome != PaymentAttemptStatus.SUCCEEDED && outcome != status) {
            throw new IllegalStateException("Conflicting terminal gateway outcomes require reconciliation");
        }
        bindGatewayOrder(orderId, orderStatus);
        status = outcome;
        nextReconcileAt = null;
        lastError = null;
    }

    private void bindGatewayOrder(String orderId, String orderStatus) {
        PaymentValidation.text(orderId, "orderId", 150);
        PaymentValidation.text(orderStatus, "orderStatus", 100);
        if (providerOrderId != null && !providerOrderId.equals(orderId)) {
            throw new IllegalArgumentException("Attempt already belongs to another gateway order");
        }
        providerOrderId = orderId;
        providerOrderStatus = orderStatus;
    }

    public record CheckoutField(String name, String value) {
        public CheckoutField {
            PaymentValidation.text(name, "field name", 100);
            Objects.requireNonNull(value, "field value must not be null");
        }

        @Override
        public String toString() {
            return "CheckoutField[name=" + name + ", value=<redacted>]";
        }
    }

    public record CheckoutSpec(PaymentEnvironment environment, String merchantId, String invoiceNumber,
            String paymentMethod, List<CheckoutField> fields, String idempotencyKey, String requestHash) {
        public CheckoutSpec {
            Objects.requireNonNull(environment, "environment must not be null");
            PaymentValidation.text(merchantId, "merchantId", 100);
            PaymentValidation.text(invoiceNumber, "invoiceNumber", 100);
            PaymentValidation.optionalText(paymentMethod, "paymentMethod", 50);
            fields = List.copyOf(Objects.requireNonNull(fields, "fields must not be null"));
            if (fields.isEmpty() || fields.stream().map(CheckoutField::name).distinct().count() != fields.size()) {
                throw new IllegalArgumentException("Checkout fields must be nonempty with unique names");
            }
            PaymentValidation.text(idempotencyKey, "idempotencyKey", 100);
            PaymentValidation.hash(requestHash);
        }

        @Override
        public String toString() {
            return "CheckoutSpec[environment=" + environment + ", invoiceNumber=" + invoiceNumber
                    + ", fields=<redacted>]";
        }
    }
}
