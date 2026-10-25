package dev.booking.sports.booking.infrastructure.outbox;

import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import dev.booking.sports.booking.domain.event.BookingCancelledEvent;
import dev.booking.sports.booking.domain.event.BookingConfirmedEvent;
import dev.booking.sports.booking.domain.event.BookingExpiredEvent;
import dev.booking.sports.booking.domain.event.BookingHeldEvent;
import dev.booking.sports.booking.domain.event.BookingOutboxMessageTypes;
import dev.booking.sports.booking.infrastructure.messaging.BookingEventMessage;
import dev.booking.sports.booking.infrastructure.messaging.BookingEventPublisher;
import dev.booking.sports.shared.outbox.OutboxEvent;
import dev.booking.sports.shared.outbox.OutboxPublicationHandler;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BookingOutboxPublicationHandler
        implements OutboxPublicationHandler {

    private final ObjectMapper objectMapper;
    private final BookingEventPublisher eventPublisher;

    @Override
    public boolean supports(String eventType) {
        return BookingOutboxMessageTypes.BOOKING_HELD.equals(eventType)
                || BookingOutboxMessageTypes.BOOKING_CONFIRMED.equals(eventType)
                || BookingOutboxMessageTypes.BOOKING_CANCELLED.equals(eventType)
                || BookingOutboxMessageTypes.BOOKING_EXPIRED.equals(eventType);
    }

    @Override
    public void publish(OutboxEvent message) throws Exception {
        validateContract(message);

        switch (message.getEventType()) {
            case BookingOutboxMessageTypes.BOOKING_HELD ->
                    objectMapper.readValue(
                            message.getPayload(),
                            BookingHeldEvent.class);
            case BookingOutboxMessageTypes.BOOKING_CONFIRMED ->
                    objectMapper.readValue(
                            message.getPayload(),
                            BookingConfirmedEvent.class);
            case BookingOutboxMessageTypes.BOOKING_CANCELLED ->
                    objectMapper.readValue(
                            message.getPayload(),
                            BookingCancelledEvent.class);
            case BookingOutboxMessageTypes.BOOKING_EXPIRED ->
                    objectMapper.readValue(
                            message.getPayload(),
                            BookingExpiredEvent.class);
            default -> throw new IllegalArgumentException(
                    "Unsupported booking event type: " + message.getEventType());
        }

        eventPublisher.publish(new BookingEventMessage(
                message.getEventId(),
                message.getAggregateType(),
                message.getAggregateId(),
                message.getEventType(),
                message.getEventVersion(),
                objectMapper.readTree(message.getPayload())));
    }

    private void validateContract(OutboxEvent message) {
        if (!BookingOutboxMessageTypes.BOOKING_AGGREGATE_TYPE.equals(
                message.getAggregateType())) {
            throw new IllegalArgumentException(
                    "Unexpected aggregate type for booking event: "
                            + message.getAggregateType());
        }
        if (message.getEventVersion()
                != BookingOutboxMessageTypes.EVENT_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported booking event version: "
                            + message.getEventVersion());
        }
    }
}
