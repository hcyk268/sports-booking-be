package dev.booking.sports.notification.service;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.stereotype.Service;

import dev.booking.sports.notification.config.BookingNotificationRabbitConfig;
import dev.booking.sports.notification.config.NotificationRabbitConfig;
import dev.booking.sports.shared.messaging.ConfirmedRabbitPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingNotificationRecoveryService {

    private final ConfirmedRabbitPublisher publisher;

    public void recover(Message original, RuntimeException failure) {
        Object header = original.getMessageProperties().getHeader(BookingNotificationRabbitConfig.RETRY_HEADER);
        int attempts = header instanceof Number n
                ? (int) Math.min(BookingNotificationRabbitConfig.MAX_RETRIES, Math.max(0L, n.longValue())) : 0;
        boolean permanent = failure instanceof JacksonException || failure instanceof IllegalArgumentException;
        Message copy = MessageBuilder.fromClonedMessage(original)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT).build();
        if (!permanent && attempts < BookingNotificationRabbitConfig.MAX_RETRIES) {
            copy.getMessageProperties().setHeader(BookingNotificationRabbitConfig.RETRY_HEADER, attempts + 1);
            publisher.publish("", BookingNotificationRabbitConfig.retryQueue(attempts + 1), copy);
            log.warn("Booking notification scheduled for retry {}", attempts + 1);
        } else {
            publisher.publish(NotificationRabbitConfig.DEAD_LETTER_EXCHANGE,
                    BookingNotificationRabbitConfig.DLQ_ROUTING_KEY, copy);
            log.error("Booking notification moved to DLQ after {} retries", attempts);
        }
    }
}
