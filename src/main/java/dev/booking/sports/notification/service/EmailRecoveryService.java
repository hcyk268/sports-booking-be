package dev.booking.sports.notification.service;

import java.io.IOException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import org.springframework.amqp.rabbit.support.DefaultMessagePropertiesConverter;
import dev.booking.sports.shared.messaging.ConfirmedRabbitPublisher;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.stereotype.Service;
import dev.booking.sports.notification.config.NotificationRabbitConfig;
import dev.booking.sports.notification.email.EmailDeliveryException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailRecoveryService {
    private final RabbitTemplate rabbitTemplate;
    private final ConfirmedRabbitPublisher publisher;

    public void recover(Message original, RuntimeException failure) {
        Object header = original.getMessageProperties().getHeaders().get(NotificationRabbitConfig.RETRY_HEADER);
        int attempts = header instanceof Number n
                ? (int) Math.min(NotificationRabbitConfig.MAX_RETRIES, Math.max(0L, n.longValue())) : 0;
        boolean permanent = failure instanceof MessageConversionException || failure instanceof IllegalArgumentException
                || (failure instanceof EmailDeliveryException e && !e.isRetryable());
        Message copy = MessageBuilder.fromClonedMessage(original)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT).build();
        if (!permanent && attempts < NotificationRabbitConfig.MAX_RETRIES) {
            copy.getMessageProperties().setHeader(NotificationRabbitConfig.RETRY_HEADER, attempts + 1);
            publisher.publish("", NotificationRabbitConfig.retryQueue(attempts + 1), copy);
            log.warn("Email scheduled for retry {}", attempts + 1);
        } else {
            publisher.publish(NotificationRabbitConfig.DEAD_LETTER_EXCHANGE,
                    NotificationRabbitConfig.EMAIL_DLQ_ROUTING_KEY, copy);
            log.error("Email moved to DLQ after {} retries; administrator replay required", attempts);
        }
    }

    public int replayDeadLetters(int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        int replayed = 0;
        while (replayed < limit && Boolean.TRUE.equals(rabbitTemplate.execute(this::replayOne))) {
            replayed++;
        }
        return replayed;
    }

    private boolean replayOne(Channel channel) throws IOException {
        var delivery = channel.basicGet(NotificationRabbitConfig.EMAIL_DLQ, false);
        if (delivery == null) return false;
        long tag = delivery.getEnvelope().getDeliveryTag();
        try {
            publisher.publish(NotificationRabbitConfig.EXCHANGE, NotificationRabbitConfig.EMAIL_ROUTING_KEY,
                    prepareReplayMessage(delivery));
            channel.basicAck(tag, false);
            return true;
        } catch (RuntimeException failure) {
            channel.basicNack(tag, false, true);
            throw failure;
        }
    }

    private Message prepareReplayMessage(GetResponse delivery) {
        var converter = new DefaultMessagePropertiesConverter();
        var props = converter.toMessageProperties(delivery.getProps(), delivery.getEnvelope(), "UTF-8");
        props.getHeaders().remove("x-death");
        props.getHeaders().remove("x-first-death-exchange");
        props.getHeaders().remove("x-first-death-queue");
        props.getHeaders().remove("x-first-death-reason");
        props.setHeader(NotificationRabbitConfig.RETRY_HEADER, 0);
        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        return new Message(delivery.getBody(), props);
    }
}
