package dev.booking.sports.notification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.booking.sports.booking.domain.event.BookingCancelledEvent;
import dev.booking.sports.booking.domain.event.BookingConfirmedEvent;
import dev.booking.sports.booking.domain.event.BookingExpiredEvent;
import dev.booking.sports.booking.domain.event.BookingOutboxMessageTypes;
import dev.booking.sports.notification.consumer.BookingNotificationMessage;
import tools.jackson.databind.ObjectMapper;

public final class BookingNotificationFixtures {
    private BookingNotificationFixtures() {}

    public static BookingNotificationMessage message(UUID customerId, String eventType) {
        UUID bookingId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("300000");
        Instant now = Instant.parse("2026-11-12T00:00:00Z");
        Object event = switch (eventType) {
            case BookingOutboxMessageTypes.BOOKING_CONFIRMED ->
                    new BookingConfirmedEvent(bookingId, "BK-TEST-001", customerId, amount, "VND", now, List.of());
            case BookingOutboxMessageTypes.BOOKING_CANCELLED ->
                    new BookingCancelledEvent(bookingId, "BK-TEST-001", customerId, amount, "VND", customerId,
                            "Changed plans", now, List.of());
            case BookingOutboxMessageTypes.BOOKING_EXPIRED ->
                    new BookingExpiredEvent(bookingId, "BK-TEST-001", customerId, amount, "VND", now, List.of());
            default -> throw new IllegalArgumentException("Unknown fixture event type");
        };
        return new BookingNotificationMessage(UUID.randomUUID(), "BOOKING", bookingId.toString(),
                eventType, 1, new ObjectMapper().valueToTree(event));
    }
}
