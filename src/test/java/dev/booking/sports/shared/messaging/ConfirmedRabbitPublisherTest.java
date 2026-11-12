package dev.booking.sports.shared.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import dev.booking.sports.shared.messaging.ConfirmedRabbitPublisher;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import dev.booking.sports.shared.config.RabbitMQProperties;

class ConfirmedRabbitPublisherTest {
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final ConfirmedRabbitPublisher publisher = new ConfirmedRabbitPublisher(rabbit,
            new RabbitMQProperties(Duration.ofMillis(50)));

    @Test void routesEnvelopeWithStableEventIdAndWaitsForBrokerAck() throws Exception {
        var message = message();
        doAnswer(call -> {
            CorrelationData correlation = call.getArgument(3);
            assertThat(correlation.getId()).isEqualTo(message.eventId().toString());
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).convertAndSend(eq("sports.payment"), eq("payment.succeeded"), eq(message), any(CorrelationData.class));

        publish(message);

        verify(rabbit).convertAndSend(eq("sports.payment"), eq("payment.succeeded"), eq(message), any(CorrelationData.class));
    }

    @Test void brokerNackIsNotAcceptedAsPublished() {
        doAnswer(call -> {
            CorrelationData correlation = call.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "nack"));
            return null;
        }).when(rabbit).convertAndSend(anyString(), anyString(), any(Object.class), any(CorrelationData.class));

        assertThatThrownBy(() -> publish(message())).isInstanceOf(AmqpException.class);
    }

    @Test void unroutableEventIsNotAcceptedAsPublished() {
        doAnswer(call -> {
            CorrelationData correlation = call.getArgument(3);
            correlation.setReturned(new ReturnedMessage(new Message(new byte[0]), 312, "NO_ROUTE", "sports.payment", "payment.succeeded"));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).convertAndSend(anyString(), anyString(), any(Object.class), any(CorrelationData.class));

        assertThatThrownBy(() -> publish(message())).isInstanceOf(AmqpException.class);
    }

    @Test void missingBrokerConfirmTimesOut() {
        assertThatThrownBy(() -> publish(message())).isInstanceOf(AmqpException.class).hasCauseInstanceOf(TimeoutException.class);
    }

    @Test void interruptedConfirmWaitPreservesInterruptFlag() {
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> publish(message())).isInstanceOf(AmqpException.class).hasCauseInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    private void publish(TestEvent message) {
        publisher.publish("sports.payment", "payment.succeeded", message, message.eventId().toString());
    }
    private TestEvent message() { return new TestEvent(UUID.randomUUID()); }
    private record TestEvent(UUID eventId) {}
}
