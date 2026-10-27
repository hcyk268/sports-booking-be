package dev.booking.sports.booking.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record BookingCancelledEvent(
        UUID bookingId,
        String bookingCode,
        UUID customerId,
        BigDecimal totalAmount,
        String currency,
        UUID cancelledBy,
        String cancellationReason,
        Instant occurredAt,
        List<BookingSlotSnapshot> slots) {

    public BookingCancelledEvent {
        Objects.requireNonNull(bookingId, "bookingId must not be null");
        Objects.requireNonNull(bookingCode, "bookingCode must not be null");
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(totalAmount, "totalAmount must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(cancelledBy, "cancelledBy must not be null");
        Objects.requireNonNull(cancellationReason, "cancellationReason must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        slots = List.copyOf(Objects.requireNonNull(slots, "slots must not be null"));
    }
}
