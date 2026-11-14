package dev.booking.sports.notification.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.booking.sports.booking.domain.event.BookingOutboxMessageTypes;

@Configuration
public class BookingNotificationRabbitConfig {

    public static final String SOURCE_EXCHANGE = "sports.booking";
    public static final String QUEUE = "sports.notification.booking";
    public static final String DLQ = QUEUE + ".dlq";
    public static final String ROUTING_KEY = "booking.notification";
    public static final String DLQ_ROUTING_KEY = "booking.notification.dlq";
    public static final String RETRY_HEADER = "x-booking-notification-retry-count";
    private static final List<Integer> RETRY_DELAYS_MS = List.of(10_000, 60_000, 300_000);
    public static final int MAX_RETRIES = RETRY_DELAYS_MS.size();

    public static String retryQueue(int attempt) {
        return QUEUE + ".retry." + attempt;
    }

    @Bean
    public Declarables bookingNotificationTopology() {
        var source = new TopicExchange(SOURCE_EXCHANGE, true, false);
        var exchange = new TopicExchange(NotificationRabbitConfig.EXCHANGE, true, false);
        var dlx = new TopicExchange(NotificationRabbitConfig.DEAD_LETTER_EXCHANGE, true, false);
        var queue = QueueBuilder.durable(QUEUE).quorum()
                .maxLength(10_000).maxLengthBytes(268_435_456)
                .overflow(QueueBuilder.Overflow.rejectPublish)
                .deadLetterExchange(NotificationRabbitConfig.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DLQ_ROUTING_KEY)
                .withArgument("x-dead-letter-strategy", "at-least-once").build();
        var dlq = QueueBuilder.durable(DLQ).quorum().maxLength(10_000).maxLengthBytes(268_435_456)
                .overflow(QueueBuilder.Overflow.rejectPublish).build();
        var topology = new ArrayList<Declarable>(List.of(source, exchange, dlx, queue, dlq,
                BindingBuilder.bind(queue).to(source).with(BookingOutboxMessageTypes.BOOKING_CONFIRMED),
                BindingBuilder.bind(queue).to(source).with(BookingOutboxMessageTypes.BOOKING_CANCELLED),
                BindingBuilder.bind(queue).to(source).with(BookingOutboxMessageTypes.BOOKING_EXPIRED),
                BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY),
                BindingBuilder.bind(dlq).to(dlx).with(DLQ_ROUTING_KEY)));
        for (int i = 0; i < MAX_RETRIES; i++) {
            topology.add(QueueBuilder.durable(retryQueue(i + 1)).quorum()
                    .ttl(RETRY_DELAYS_MS.get(i)).maxLength(10_000).maxLengthBytes(67_108_864)
                    .overflow(QueueBuilder.Overflow.rejectPublish)
                    .deadLetterExchange(NotificationRabbitConfig.EXCHANGE).deadLetterRoutingKey(ROUTING_KEY)
                    .withArgument("x-dead-letter-strategy", "at-least-once").build());
        }
        return new Declarables(topology);
    }
}
