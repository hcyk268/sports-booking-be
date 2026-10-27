package dev.booking.sports.booking.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import tools.jackson.databind.ObjectMapper;

import dev.booking.sports.booking.domain.event.BookingOutboxMessageTypes;
import dev.booking.sports.shared.config.RabbitMQProperties;

@ExtendWith(MockitoExtension.class)
class BookingEventPublisherTest {

    private static final UUID EVENT_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Mock
    private RabbitTemplate rabbitTemplate;

    private BookingEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new BookingEventPublisher(
                rabbitTemplate,
                new RabbitMQProperties(Duration.ofSeconds(5)));
    }

    @Test
    void publishesUsingEventTypeAsRoutingKeyAndWaitsForConfirm() {
        BookingEventMessage message = message();
        stubConfirm(new CorrelationData.Confirm(true, "ok"));

        publisher.publish(message);

        verify(rabbitTemplate).convertAndSend(
                eq(BookingRabbitConfig.EVENT_EXCHANGE),
                eq(BookingOutboxMessageTypes.BOOKING_HELD),
                eq(message),
                any(CorrelationData.class));
    }

    @Test
    void rejectsBrokerNack() {
        BookingEventMessage message = message();
        stubConfirm(new CorrelationData.Confirm(false, "exchange unavailable"));

        assertThatThrownBy(() -> publisher.publish(message))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining(EVENT_ID.toString());
    }

    @Test
    void rejectsUnroutableMessage() {
        BookingEventMessage message = message();
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.setReturned(new ReturnedMessage(
                    MessageBuilder.withBody(new byte[0]).build(),
                    312,
                    "NO_ROUTE",
                    BookingRabbitConfig.EVENT_EXCHANGE,
                    BookingOutboxMessageTypes.BOOKING_HELD));
            correlationData.getFuture().complete(
                    new CorrelationData.Confirm(true, "ok"));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                any(String.class),
                any(String.class),
                any(Object.class),
                any(CorrelationData.class));

        assertThatThrownBy(() -> publisher.publish(message))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining("unroutable")
                .hasMessageContaining(EVENT_ID.toString());
    }

    @Test
    void propagatesRabbitFailureForOutboxRetry() {
        BookingEventMessage message = message();
        doThrow(new AmqpException("broker down"))
                .when(rabbitTemplate)
                .convertAndSend(
                        eq(BookingRabbitConfig.EVENT_EXCHANGE),
                        eq(BookingOutboxMessageTypes.BOOKING_HELD),
                        eq(message),
                        any(CorrelationData.class));

        assertThatThrownBy(() -> publisher.publish(message))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining("broker down");
    }

    private void stubConfirm(CorrelationData.Confirm confirm) {
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().complete(confirm);
            return null;
        }).when(rabbitTemplate).convertAndSend(
                any(String.class),
                any(String.class),
                any(Object.class),
                any(CorrelationData.class));
    }

    private BookingEventMessage message() {
        return new BookingEventMessage(
                EVENT_ID,
                BookingOutboxMessageTypes.BOOKING_AGGREGATE_TYPE,
                "11111111-1111-1111-1111-111111111111",
                BookingOutboxMessageTypes.BOOKING_HELD,
                BookingOutboxMessageTypes.EVENT_VERSION,
                new ObjectMapper().readTree("{\"bookingCode\":\"BK-001\"}"));
    }
}
