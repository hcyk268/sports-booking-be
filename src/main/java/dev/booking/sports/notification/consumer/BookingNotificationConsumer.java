package dev.booking.sports.notification.consumer;

import java.io.IOException;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;
import dev.booking.sports.notification.application.BookingNotificationHandler;
import dev.booking.sports.notification.config.BookingNotificationRabbitConfig;
import dev.booking.sports.notification.service.BookingNotificationRecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingNotificationConsumer {

    private final ObjectMapper objectMapper;
    private final BookingNotificationHandler handler;
    private final BookingNotificationRecoveryService recovery;

    @RabbitListener(id = "bookingNotificationConsumer", queues = BookingNotificationRabbitConfig.QUEUE,
            messageConverter = "rawNotificationMessageConverter")
    public void consume(Message raw, Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            handler.handle(objectMapper.readValue(raw.getBody(), BookingNotificationMessage.class));
        } catch (RuntimeException failure) {
            try {
                recovery.recover(raw, failure);
            } catch (RuntimeException recoveryFailure) {
                log.error("Could not confirm booking notification recovery; retaining original delivery", recoveryFailure);
                try { Thread.sleep(1_000); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                channel.basicNack(deliveryTag, false, true);
                return;
            }
        }
        channel.basicAck(deliveryTag, false);
    }
}
