package dev.booking.sports.booking.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record BookingConfirmedEvent(
        UUID bookingId,
        String bookingCode,
        UUID customerId,
        BigDecimal totalAmount,
        String currency,
        Instant occurredAt,
        List<BookingSlotSnapshot> slots) {

    public BookingConfirmedEvent {
        Objects.requireNonNull(bookingId, "bookingId must not be null");
        Objects.requireNonNull(bookingCode, "bookingCode must not be null");
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(totalAmount, "totalAmount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        slots = List.copyOf(Objects.requireNonNull(slots, "slots must not be null"));
    }
}
