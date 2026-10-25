package dev.booking.sports.booking.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import dev.booking.sports.booking.domain.enums.BookingSlotStatus;
import dev.booking.sports.booking.domain.enums.BookingStatus;
import dev.booking.sports.shared.persistence.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bookings")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Booking extends BaseEntity {

    private static final Pattern CURRENCY_PATTERN = Pattern.compile("[A-Z]{3}");
    private static final Pattern SHA_256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    @Column(name = "booking_code", nullable = false, length = 30, updatable = false)
    private String bookingCode;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BookingStatus status;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    @Column(name = "idempotency_key", nullable = false, length = 100, updatable = false)
    private String idempotencyKey;

    @Column(
            name = "idempotency_request_hash",
            nullable = false,
            length = 64,
            updatable = false)
    private String idempotencyRequestHash;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(
            mappedBy = "booking",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("startAt ASC, id ASC")
    private List<BookingSlot> slots = new ArrayList<>();

    @OneToMany(
            mappedBy = "booking",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC, id ASC")
    private List<BookingStatusHistory> statusHistory = new ArrayList<>();

    public static Booking hold(
            String bookingCode,
            UUID customerId,
            String currency,
            String idempotencyKey,
            String idempotencyRequestHash,
            Instant holdExpiresAt,
            Instant now,
            List<BookingSlot.Spec> requestedSlots) {
        validateText(bookingCode, "bookingCode", 30);
        Objects.requireNonNull(customerId, "customerId must not be null");
        validateCurrency(currency);
        validateText(idempotencyKey, "idempotencyKey", 100);
        if (idempotencyRequestHash == null
                || !SHA_256_PATTERN.matcher(idempotencyRequestHash).matches()) {
            throw new IllegalArgumentException(
                    "idempotencyRequestHash must be a lowercase SHA-256 hash");
        }
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(holdExpiresAt, "holdExpiresAt must not be null");
        if (!holdExpiresAt.isAfter(now)) {
            throw new IllegalArgumentException("holdExpiresAt must be in the future");
        }
        if (requestedSlots == null || requestedSlots.isEmpty()) {
            throw new IllegalArgumentException("At least one booking slot is required");
        }
        requestedSlots.forEach(slot -> {
            Objects.requireNonNull(slot, "booking slot must not be null");
            if (!slot.startAt().isAfter(now)) {
                throw new IllegalArgumentException(
                        "Booking slots must start in the future");
            }
        });
        Instant earliestSlotStart = requestedSlots.stream()
                .map(BookingSlot.Spec::startAt)
                .min(Instant::compareTo)
                .orElseThrow();
        if (holdExpiresAt.isAfter(earliestSlotStart)) {
            throw new IllegalArgumentException(
                    "holdExpiresAt must not be after the earliest slot start");
        }

        Booking booking = new Booking();
        booking.bookingCode = bookingCode;
        booking.customerId = customerId;
        booking.currency = currency;
        booking.idempotencyKey = idempotencyKey;
        booking.idempotencyRequestHash = idempotencyRequestHash;
        booking.holdExpiresAt = holdExpiresAt;
        booking.status = BookingStatus.HELD;
        booking.totalAmount = BigDecimal.ZERO;

        requestedSlots.forEach(booking::addSlot);
        booking.statusHistory.add(BookingStatusHistory.record(
                booking, null, BookingStatus.HELD, customerId, null));
        return booking;
    }

    public List<BookingSlot> getSlots() {
        return List.copyOf(slots);
    }

    public List<BookingStatusHistory> getStatusHistory() {
        return List.copyOf(statusHistory);
    }

    public boolean hasIdempotencyRequestHash(String requestHash) {
        return idempotencyRequestHash.equals(requestHash);
    }

    public void startPayment(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        requireStatus(BookingStatus.HELD);
        if (!now.isBefore(holdExpiresAt)) {
            throw new IllegalStateException("Booking hold has expired");
        }
        transitionTo(
                BookingStatus.PENDING_PAYMENT,
                BookingSlotStatus.PENDING_PAYMENT,
                null,
                null);
    }

    public void confirm(Instant paymentCompletedAt) {
        Objects.requireNonNull(paymentCompletedAt, "paymentCompletedAt must not be null");
        if (status != BookingStatus.HELD
                && status != BookingStatus.PENDING_PAYMENT) {
            throw invalidTransition(BookingStatus.CONFIRMED);
        }
        if (paymentCompletedAt.isAfter(holdExpiresAt)) {
            throw new IllegalStateException(
                    "A booking cannot be confirmed after its hold expired");
        }
        confirmedAt = paymentCompletedAt;
        transitionTo(
                BookingStatus.CONFIRMED,
                BookingSlotStatus.CONFIRMED,
                null,
                null);
    }

    public void cancel(UUID actorId, String reason, Instant cancelledAt) {
        Objects.requireNonNull(actorId, "actorId must not be null");
        Objects.requireNonNull(cancelledAt, "cancelledAt must not be null");
        validateText(reason, "cancellation reason", 500);
        if (status.isTerminal()) {
            throw invalidTransition(BookingStatus.CANCELLED);
        }

        this.cancelledBy = actorId;
        this.cancelledAt = cancelledAt;
        this.cancellationReason = reason;
        transitionTo(
                BookingStatus.CANCELLED,
                BookingSlotStatus.CANCELLED,
                actorId,
                reason);
    }

    public void expire(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (status != BookingStatus.HELD
                && status != BookingStatus.PENDING_PAYMENT) {
            throw invalidTransition(BookingStatus.EXPIRED);
        }
        if (now.isBefore(holdExpiresAt)) {
            throw new IllegalStateException("Booking hold has not expired yet");
        }
        transitionTo(
                BookingStatus.EXPIRED,
                BookingSlotStatus.EXPIRED,
                null,
                "Booking hold expired");
    }

    public void complete(Instant completedAt) {
        Objects.requireNonNull(completedAt, "completedAt must not be null");
        requireStatus(BookingStatus.CONFIRMED);
        if (completedAt.isBefore(confirmedAt)) {
            throw new IllegalArgumentException(
                    "completedAt must not precede confirmedAt");
        }
        this.completedAt = completedAt;
        transitionTo(
                BookingStatus.COMPLETED,
                BookingSlotStatus.COMPLETED,
                null,
                null);
    }

    private void addSlot(BookingSlot.Spec spec) {
        Objects.requireNonNull(spec, "booking slot must not be null");
        if (slots.stream().anyMatch(existing -> existing.overlaps(spec))) {
            throw new IllegalArgumentException(
                    "Booking slots for the same court must not overlap");
        }
        BookingSlot slot = BookingSlot.hold(this, spec, currency);
        slots.add(slot);
        totalAmount = totalAmount.add(slot.getPriceAmount());
    }

    private void transitionTo(
            BookingStatus newStatus,
            BookingSlotStatus newSlotStatus,
            UUID changedBy,
            String reason) {
        BookingStatus previousStatus = status;
        status = newStatus;
        slots.forEach(slot -> slot.changeStatus(newSlotStatus));
        statusHistory.add(BookingStatusHistory.record(
                this, previousStatus, newStatus, changedBy, reason));
    }

    private void requireStatus(BookingStatus expectedStatus) {
        if (status != expectedStatus) {
            throw invalidTransition(expectedStatus);
        }
    }

    private IllegalStateException invalidTransition(BookingStatus targetStatus) {
        return new IllegalStateException(
                "Cannot transition booking from " + status + " to " + targetStatus);
    }

    private static void validateCurrency(String currency) {
        if (currency == null || !CURRENCY_PATTERN.matcher(currency).matches()) {
            throw new IllegalArgumentException(
                    "currency must be a three-letter uppercase code");
        }
    }

    private static void validateText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + " must contain between 1 and " + maxLength + " characters");
        }
    }
}
