package dev.booking.sports.payment.infrastructure.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import dev.booking.sports.payment.domain.event.PaymentOutboxMessageTypes;
import dev.booking.sports.shared.config.RabbitMQProperties;
import tools.jackson.databind.ObjectMapper;

class PaymentEventPublisherTest {
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final PaymentEventPublisher publisher = new PaymentEventPublisher(rabbit,
            new RabbitMQProperties(Duration.ofMillis(50)));

    @Test void routesEnvelopeWithStableEventIdAndWaitsForBrokerAck() throws Exception {
        var message = message();
        doAnswer(call -> {
            CorrelationData correlation = call.getArgument(3);
            assertThat(correlation.getId()).isEqualTo(message.eventId().toString());
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).convertAndSend(eq("sports.payment"), eq("payment.succeeded"), eq(message), any(CorrelationData.class));

        publisher.publish(message);

        verify(rabbit).convertAndSend(eq("sports.payment"), eq("payment.succeeded"), eq(message), any(CorrelationData.class));
    }

    @Test void brokerNackIsNotAcceptedAsPublished() {
        doAnswer(call -> {
            CorrelationData correlation = call.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "nack"));
            return null;
        }).when(rabbit).convertAndSend(anyString(), anyString(), any(Object.class), any(CorrelationData.class));

        assertThatThrownBy(() -> publisher.publish(message())).isInstanceOf(AmqpException.class);
    }

    @Test void unroutableEventIsNotAcceptedAsPublished() {
        doAnswer(call -> {
            CorrelationData correlation = call.getArgument(3);
            correlation.setReturned(new ReturnedMessage(new Message(new byte[0]), 312, "NO_ROUTE", "sports.payment", "payment.succeeded"));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).convertAndSend(anyString(), anyString(), any(Object.class), any(CorrelationData.class));

        assertThatThrownBy(() -> publisher.publish(message())).isInstanceOf(AmqpException.class);
    }

    @Test void missingBrokerConfirmTimesOut() {
        assertThatThrownBy(() -> publisher.publish(message())).isInstanceOf(TimeoutException.class);
    }

    @Test void interruptedConfirmWaitPreservesInterruptFlag() {
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> publisher.publish(message())).isInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    private PaymentEventMessage message() {
        return new PaymentEventMessage(UUID.randomUUID(), "PAYMENT", UUID.randomUUID().toString(),
                PaymentOutboxMessageTypes.SUCCEEDED, PaymentOutboxMessageTypes.VERSION,
                new ObjectMapper().readTree("{\"paymentId\":\"payment-id\"}"));
    }
}
