package dev.booking.sports.shared.messaging;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import dev.booking.sports.shared.config.RabbitMQProperties;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ConfirmedRabbitPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQProperties properties;

    public void publish(String exchange, String routingKey, Message message) {
        publish(exchange, routingKey, message, UUID.randomUUID().toString());
    }

    public void publish(String exchange, String routingKey, Object payload, String correlationId) {
        var correlation = new CorrelationData(correlationId);
        try {
            if (payload instanceof Message message) {
                rabbitTemplate.send(exchange, routingKey, message, correlation);
            } else {
                rabbitTemplate.convertAndSend(exchange, routingKey, payload, correlation);
            }
            var confirm = correlation.getFuture().get(
                    properties.publisherConfirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (correlation.getReturned() != null) {
                throw new AmqpException("Broker returned unroutable message " + correlationId);
            }
            if (confirm == null || !confirm.ack()) {
                throw new AmqpException("Broker did not acknowledge message " + correlationId
                        + ": " + (confirm == null ? "no confirm" : confirm.reason()));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Interrupted waiting for publish confirm " + correlationId, exception);
        } catch (AmqpException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AmqpException("Could not confirm publication " + correlationId, exception);
        }
    }
}
