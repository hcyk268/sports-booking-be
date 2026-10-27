package dev.booking.sports.booking.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record BookingSlotSnapshot(
        UUID bookingSlotId,
        UUID courtId,
        Instant startAt,
        Instant endAt,
        BigDecimal priceAmount) {

    public BookingSlotSnapshot {
        Objects.requireNonNull(bookingSlotId, "bookingSlotId must not be null");
        Objects.requireNonNull(courtId, "courtId must not be null");
        Objects.requireNonNull(startAt, "startAt must not be null");
        Objects.requireNonNull(endAt, "endAt must not be null");
        Objects.requireNonNull(priceAmount, "priceAmount must not be null");

        if (!startAt.isBefore(endAt)) {
            throw new IllegalArgumentException("startAt must precede endAt");
        }
        if (priceAmount.signum() <= 0) {
            throw new IllegalArgumentException("priceAmount must be positive");
        }
    }
}
