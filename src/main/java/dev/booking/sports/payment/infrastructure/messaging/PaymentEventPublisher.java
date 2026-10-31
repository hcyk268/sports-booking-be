package dev.booking.sports.payment.infrastructure.messaging;

import java.util.concurrent.TimeUnit;

import dev.booking.sports.shared.config.RabbitMQProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class PaymentEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQProperties rabbitMQProperties;

    public void publish(PaymentEventMessage message) throws Exception {
        var correlation = new CorrelationData(message.eventId().toString());
        rabbitTemplate.convertAndSend(PaymentRabbitConfig.EXCHANGE, message.eventType(), message, correlation);
        try {
            var confirm = correlation.getFuture().get(
                    rabbitMQProperties.publisherConfirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (correlation.getReturned() != null || confirm == null || !confirm.isAck()) {
                throw new AmqpException("Payment event was not routed and acknowledged: " + message.eventId());
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        }
    }
}
