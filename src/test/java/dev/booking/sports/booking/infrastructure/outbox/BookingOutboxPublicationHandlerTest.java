package dev.booking.sports.booking.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tools.jackson.databind.ObjectMapper;

import dev.booking.sports.booking.domain.event.BookingCancelledEvent;
import dev.booking.sports.booking.domain.event.BookingConfirmedEvent;
import dev.booking.sports.booking.domain.event.BookingExpiredEvent;
import dev.booking.sports.booking.domain.event.BookingHeldEvent;
import dev.booking.sports.booking.domain.event.BookingOutboxMessageTypes;
import dev.booking.sports.booking.domain.event.BookingSlotSnapshot;
import dev.booking.sports.booking.infrastructure.messaging.BookingEventMessage;
import dev.booking.sports.booking.infrastructure.messaging.BookingEventPublisher;
import dev.booking.sports.shared.outbox.OutboxEvent;

@ExtendWith(MockitoExtension.class)
class BookingOutboxPublicationHandlerTest {

    private static final UUID BOOKING_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CUSTOMER_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ACTOR_ID =
            UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant OCCURRED_AT =
            Instant.parse("2026-10-25T08:00:00Z");

    @Mock
    private BookingEventPublisher eventPublisher;

    private ObjectMapper objectMapper;
    private BookingOutboxPublicationHandler handler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new BookingOutboxPublicationHandler(
                objectMapper,
                eventPublisher);
    }

    @Test
    void supportsOnlyBookingEventTypes() {
        assertThat(handler.supports(BookingOutboxMessageTypes.BOOKING_HELD))
                .isTrue();
        assertThat(handler.supports(BookingOutboxMessageTypes.BOOKING_CONFIRMED))
                .isTrue();
        assertThat(handler.supports(BookingOutboxMessageTypes.BOOKING_CANCELLED))
                .isTrue();
        assertThat(handler.supports(BookingOutboxMessageTypes.BOOKING_EXPIRED))
                .isTrue();
        assertThat(handler.supports("identity.password-changed")).isFalse();
    }

    @ParameterizedTest
    @MethodSource("bookingEvents")
    void validatesAndPublishesBookingEventToRabbitMq(
            String eventType,
            Object event) throws Exception {
        String payload = objectMapper.writeValueAsString(event);
        OutboxEvent message = outbox(
                eventType,
                BookingOutboxMessageTypes.EVENT_VERSION,
                payload);

        handler.publish(message);

        verify(eventPublisher).publish(new BookingEventMessage(
                message.getEventId(),
                BookingOutboxMessageTypes.BOOKING_AGGREGATE_TYPE,
                BOOKING_ID.toString(),
                eventType,
                BookingOutboxMessageTypes.EVENT_VERSION,
                objectMapper.readTree(payload)));
    }

    @Test
    void rejectsUnsupportedContractVersion() {
        OutboxEvent message = outbox(
                BookingOutboxMessageTypes.BOOKING_HELD,
                2,
                "{}");

        assertThatThrownBy(() -> handler.publish(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported booking event version: 2");
    }

    @Test
    void rejectsUnexpectedAggregateType() {
        OutboxEvent message = new OutboxEvent(
                "USER",
                BOOKING_ID.toString(),
                BookingOutboxMessageTypes.BOOKING_HELD,
                BookingOutboxMessageTypes.EVENT_VERSION,
                "{}");

        assertThatThrownBy(() -> handler.publish(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unexpected aggregate type for booking event: USER");
    }

    private OutboxEvent outbox(
            String eventType,
            int eventVersion,
            String payload) {
        return new OutboxEvent(
                BookingOutboxMessageTypes.BOOKING_AGGREGATE_TYPE,
                BOOKING_ID.toString(),
                eventType,
                eventVersion,
                payload);
    }

    private static Stream<Arguments> bookingEvents() {
        List<BookingSlotSnapshot> slots = List.of(slot());
        return Stream.of(
                Arguments.of(
                        BookingOutboxMessageTypes.BOOKING_HELD,
                        new BookingHeldEvent(
                                BOOKING_ID,
                                "BK-20261025-001",
                                CUSTOMER_ID,
                                new BigDecimal("400000.00"),
                                "VND",
                                OCCURRED_AT.plusSeconds(600),
                                OCCURRED_AT,
                                slots)),
                Arguments.of(
                        BookingOutboxMessageTypes.BOOKING_CONFIRMED,
                        new BookingConfirmedEvent(
                                BOOKING_ID,
                                "BK-20261025-001",
                                CUSTOMER_ID,
                                new BigDecimal("400000.00"),
                                "VND",
                                OCCURRED_AT,
                                slots)),
                Arguments.of(
                        BookingOutboxMessageTypes.BOOKING_CANCELLED,
                        new BookingCancelledEvent(
                                BOOKING_ID,
                                "BK-20261025-001",
                                CUSTOMER_ID,
                                new BigDecimal("400000.00"),
                                "VND",
                                ACTOR_ID,
                                "Customer changed plans",
                                OCCURRED_AT,
                                slots)),
                Arguments.of(
                        BookingOutboxMessageTypes.BOOKING_EXPIRED,
                        new BookingExpiredEvent(
                                BOOKING_ID,
                                "BK-20261025-001",
                                CUSTOMER_ID,
                                new BigDecimal("400000.00"),
                                "VND",
                                OCCURRED_AT,
                                slots)));
    }

    private static BookingSlotSnapshot slot() {
        return new BookingSlotSnapshot(
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                UUID.fromString("55555555-5555-5555-5555-555555555555"),
                Instant.parse("2026-10-26T01:00:00Z"),
                Instant.parse("2026-10-26T03:00:00Z"),
                new BigDecimal("400000.00"));
    }
}
