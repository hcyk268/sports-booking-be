package dev.booking.sports.booking.infrastructure.messaging;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import dev.booking.sports.shared.config.RabbitMQProperties;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BookingEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQProperties rabbitMQProperties;

    public void publish(BookingEventMessage message) {
        CorrelationData correlationData =
                new CorrelationData(message.eventId().toString());

        try {
            rabbitTemplate.convertAndSend(
                    BookingRabbitConfig.EVENT_EXCHANGE,
                    message.eventType(),
                    message,
                    correlationData);

            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(
                            rabbitMQProperties.publisherConfirmTimeout().toMillis(),
                            TimeUnit.MILLISECONDS);
            ReturnedMessage returned = correlationData.getReturned();

            if (returned != null) {
                throw new AmqpException(
                        "Broker returned unroutable booking event "
                                + message.eventId()
                                + " for routing key "
                                + message.eventType());
            }
            if (confirm == null || !confirm.isAck()) {
                String reason = confirm == null
                        ? "no confirm received"
                        : confirm.getReason();
                throw new AmqpException(
                        "Broker nacked booking event "
                                + message.eventId()
                                + ": "
                                + reason);
            }
        } catch (AmqpException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while waiting for booking event publisher confirm "
                            + message.eventId(),
                    exception);
        } catch (TimeoutException exception) {
            throw new IllegalStateException(
                    "Timed out waiting for booking event publisher confirm "
                            + message.eventId(),
                    exception);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not publish booking event " + message.eventId(),
                    exception);
        }
    }
}
