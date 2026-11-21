package dev.booking.sports.payment.infrastructure.messaging;

import java.util.UUID;

import dev.booking.sports.booking.domain.event.BookingCancelledEvent;
import dev.booking.sports.booking.domain.event.BookingOutboxMessageTypes;

public record BookingCancellationMessage(UUID eventId, String aggregateType, String aggregateId,
        String eventType, int eventVersion, BookingCancelledEvent payload) {

    public BookingCancellationMessage {
        if (eventId == null || payload == null
                || !BookingOutboxMessageTypes.BOOKING_AGGREGATE_TYPE.equals(aggregateType)
                || !BookingOutboxMessageTypes.BOOKING_CANCELLED.equals(eventType)
                || eventVersion != BookingOutboxMessageTypes.EVENT_VERSION
                || !payload.bookingId().toString().equals(aggregateId)) {
            throw new IllegalArgumentException("Invalid booking cancellation envelope");
        }
    }
}
