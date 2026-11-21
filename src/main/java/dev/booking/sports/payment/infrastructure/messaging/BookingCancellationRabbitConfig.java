package dev.booking.sports.payment.infrastructure.messaging;

import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.booking.sports.booking.domain.event.BookingOutboxMessageTypes;

@Configuration
public class BookingCancellationRabbitConfig {

    public static final String SOURCE_EXCHANGE = "sports.booking";
    public static final String EXCHANGE = "sports.payment.booking-cancellation";
    public static final String QUEUE = "sports.payment.booking-cancelled";
    public static final String DLQ = QUEUE + ".dlq";
    public static final String ROUTING_KEY = "booking.cancelled";
    public static final String RETRY_HEADER = "x-payment-cancellation-retry-count";
    private static final List<Integer> RETRY_DELAYS_MS = List.of(10_000, 60_000, 300_000);
    public static final int MAX_RETRIES = RETRY_DELAYS_MS.size();

    public static String retryQueue(int attempt) {
        return QUEUE + ".retry." + attempt;
    }

    @Bean
    public MessageConverter rawPaymentCancellationMessageConverter() {
        return new MessageConverter() {
            @Override
            public Object fromMessage(Message message) {
                return message.getBody();
            }

            @Override
            public Message toMessage(Object value, MessageProperties properties) {
                throw new UnsupportedOperationException("Receive-only payment cancellation converter");
            }
        };
    }

    @Bean
    public Declarables bookingPaymentCancellationTopology() {
        var source = new TopicExchange(SOURCE_EXCHANGE, true, false);
        var exchange = new TopicExchange(EXCHANGE, true, false);
        var queue = QueueBuilder.durable(QUEUE).quorum()
                .maxLength(10_000).maxLengthBytes(67_108_864)
                .overflow(QueueBuilder.Overflow.rejectPublish).build();
        var dlq = QueueBuilder.durable(DLQ).quorum()
                .maxLength(10_000).maxLengthBytes(67_108_864)
                .overflow(QueueBuilder.Overflow.rejectPublish).build();
        var topology = new ArrayList<Declarable>(List.of(source, exchange, queue, dlq,
                BindingBuilder.bind(queue).to(source).with(BookingOutboxMessageTypes.BOOKING_CANCELLED),
                BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY)));
        for (int i = 0; i < MAX_RETRIES; i++) {
            topology.add(QueueBuilder.durable(retryQueue(i + 1)).quorum()
                    .ttl(RETRY_DELAYS_MS.get(i)).maxLengthBytes(67_108_864)
                    .overflow(QueueBuilder.Overflow.rejectPublish)
                    .deadLetterExchange(EXCHANGE).deadLetterRoutingKey(ROUTING_KEY)
                    .withArgument("x-dead-letter-strategy", "at-least-once").build());
        }
        return new Declarables(topology);
    }
}
