package dev.booking.sports.booking.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import dev.booking.sports.booking.domain.enums.BookingSlotStatus;
import dev.booking.sports.shared.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "booking_slots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookingSlot extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "court_id", nullable = false, updatable = false)
    private UUID courtId;

    @Column(name = "start_at", nullable = false, updatable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false, updatable = false)
    private Instant endAt;

    @Column(name = "price_amount", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal priceAmount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BookingSlotStatus status;

    @Version
    @Column(nullable = false)
    private Long version;

    static BookingSlot hold(
            Booking booking,
            Spec spec,
            String currency) {
        BookingSlot slot = new BookingSlot();
        slot.booking = booking;
        slot.courtId = spec.courtId();
        slot.startAt = spec.startAt();
        slot.endAt = spec.endAt();
        slot.priceAmount = spec.priceAmount();
        slot.currency = currency;
        slot.status = BookingSlotStatus.HELD;
        return slot;
    }

    boolean overlaps(Spec candidate) {
        return courtId.equals(candidate.courtId())
                && startAt.isBefore(candidate.endAt())
                && candidate.startAt().isBefore(endAt);
    }

    void changeStatus(BookingSlotStatus newStatus) {
        status = newStatus;
    }

    public record Spec(
            UUID courtId,
            Instant startAt,
            Instant endAt,
            BigDecimal priceAmount) {

        public Spec {
            Objects.requireNonNull(courtId, "courtId must not be null");
            Objects.requireNonNull(startAt, "startAt must not be null");
            Objects.requireNonNull(endAt, "endAt must not be null");
            Objects.requireNonNull(priceAmount, "priceAmount must not be null");

            if (!startAt.isBefore(endAt)) {
                throw new IllegalArgumentException("Slot startAt must precede endAt");
            }
            if (priceAmount.signum() <= 0) {
                throw new IllegalArgumentException("Slot priceAmount must be positive");
            }
        }
    }
}
