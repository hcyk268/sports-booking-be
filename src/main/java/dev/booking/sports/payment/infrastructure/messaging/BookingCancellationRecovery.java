package dev.booking.sports.payment.infrastructure.messaging;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.stereotype.Service;

import dev.booking.sports.shared.messaging.ConfirmedRabbitPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingCancellationRecovery {

    private final ConfirmedRabbitPublisher publisher;

    public void recover(Message original, RuntimeException failure) {
        Object header = original.getMessageProperties().getHeader(BookingCancellationRabbitConfig.RETRY_HEADER);
        int attempts = header instanceof Number n
                ? (int) Math.min(BookingCancellationRabbitConfig.MAX_RETRIES, Math.max(0L, n.longValue())) : 0;
        boolean permanent = failure instanceof JacksonException || failure instanceof IllegalArgumentException;
        Message copy = MessageBuilder.fromClonedMessage(original)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT).build();
        if (!permanent && attempts < BookingCancellationRabbitConfig.MAX_RETRIES) {
            copy.getMessageProperties().setHeader(BookingCancellationRabbitConfig.RETRY_HEADER, attempts + 1);
            publisher.publish("", BookingCancellationRabbitConfig.retryQueue(attempts + 1), copy);
            log.warn("Booking payment cancellation scheduled for retry {}", attempts + 1);
        } else {
            publisher.publish("", BookingCancellationRabbitConfig.DLQ, copy);
            log.error("Booking payment cancellation moved to DLQ after {} retries", attempts);
        }
    }
}
