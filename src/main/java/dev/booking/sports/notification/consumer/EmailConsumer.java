package dev.booking.sports.notification.consumer;

import java.io.IOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import com.rabbitmq.client.Channel;
import dev.booking.sports.notification.config.NotificationRabbitConfig;
import dev.booking.sports.notification.delivery.EmailDeliveryDedupService;
import dev.booking.sports.notification.email.EmailQueueMessage;
import dev.booking.sports.notification.email.EmailSender;
import dev.booking.sports.notification.service.EmailRecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailConsumer {
    private final EmailSender emailSender;
    private final EmailDeliveryDedupService deliveryDedupService;
    private final JacksonJsonMessageConverter converter;
    private final EmailRecoveryService recovery;

    // Convert inside the listener so malformed messages also use confirmed DLQ publication.
    @RabbitListener(queues = NotificationRabbitConfig.EMAIL_QUEUE, messageConverter = "rawEmailMessageConverter")
    public void consume(Message raw, Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            Object decoded = converter.fromMessage(raw);
            if (!(decoded instanceof EmailQueueMessage message)
                    || message.outboxEventId() == null || message.email() == null) {
                throw new MessageConversionException("Invalid email queue message");
            }
            deliveryDedupService.deliverOnce(message.outboxEventId(), () -> emailSender.send(message.email()));
        } catch (RuntimeException failure) {
            try {
                recovery.recover(raw, failure);
            } catch (RuntimeException recoveryFailure) {
                log.error("Could not confirm email retry/DLQ publication; retaining original delivery", recoveryFailure);
                // Bound the requeue rate if a target queue is full while the source remains available.
                try { Thread.sleep(1_000); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                channel.basicNack(deliveryTag, false, true);
                return;
            }
        }
        // The send transaction or retry/DLQ publication has completed before ACK.
        channel.basicAck(deliveryTag, false);
    }
}
