package dev.booking.sports.payment.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import dev.booking.sports.payment.domain.enums.PaymentStatus;
import dev.booking.sports.shared.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor
public class Payment extends BaseEntity {

    @Column(name = "booking_id", nullable = false, updatable = false)
    private UUID bookingId;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "settled_transaction_id")
    private UUID settledTransactionId;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC, id ASC")
    private List<PaymentStatusHistory> statusHistory = new ArrayList<>();

    public static Payment pending(UUID bookingId, BigDecimal amount, String currency,
            Instant expiresAt, Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (!now.isBefore(expiresAt)) {
            throw new IllegalArgumentException("Payment deadline must be in the future");
        }
        Payment payment = new Payment();
        payment.bookingId = Objects.requireNonNull(bookingId, "bookingId must not be null");
        payment.amount = PaymentValidation.vndAmount(amount);
        payment.currency = PaymentValidation.vnd(currency);
        payment.expiresAt = expiresAt;
        payment.status = PaymentStatus.PENDING;
        payment.statusHistory.add(PaymentStatusHistory.record(payment, null, payment.status, null, null));
        return payment;
    }

    public List<PaymentStatusHistory> getStatusHistory() {
        return List.copyOf(statusHistory);
    }

    public void requireCheckoutAllowed(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (status != PaymentStatus.PENDING || !now.isBefore(expiresAt)) {
            throw new IllegalStateException("Payment is not open for checkout");
        }
    }

    /** Application must also lock and confirm the booking in this transaction. */
    public void settle(PaymentTransaction transaction, Instant now) {
        Objects.requireNonNull(transaction, "transaction must not be null");
        Objects.requireNonNull(now, "now must not be null");
        UUID transactionId = Objects.requireNonNull(transaction.getId(), "Receipt must be persisted first");
        if (status == PaymentStatus.SUCCEEDED && transactionId.equals(settledTransactionId)) {
            return;
        }
        requireCheckoutAllowed(now);
        transaction.requireApplicableTo(this);
        transaction.markApplied();
        settledTransactionId = transactionId;
        settledAt = now;
        transition(PaymentStatus.SUCCEEDED, null, null);
    }

    public void expire(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (status == PaymentStatus.EXPIRED) {
            return;
        }
        if (status != PaymentStatus.PENDING || now.isBefore(expiresAt)) {
            throw new IllegalStateException("Payment cannot expire in its current state/time");
        }
        transition(PaymentStatus.EXPIRED, null, "Booking hold expired");
    }

    public void cancel(UUID actorId, String reason) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        PaymentValidation.text(reason, "reason", 1000);
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Only a pending payment can be cancelled; settled payments need a refund");
        }
        transition(PaymentStatus.CANCELLED, actorId, reason);
    }

    private void transition(PaymentStatus target, UUID actorId, String reason) {
        statusHistory.add(PaymentStatusHistory.record(this, status, target, actorId, reason));
        status = target;
    }
}
