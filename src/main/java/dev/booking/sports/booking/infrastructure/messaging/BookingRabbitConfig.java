package dev.booking.sports.booking.infrastructure.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BookingRabbitConfig {

    public static final String EVENT_EXCHANGE = "sports.booking";
    public static final String EVENT_QUEUE = "sports.booking.events";
    public static final String EVENT_ROUTING_PATTERN = "booking.#";

    @Bean
    public TopicExchange bookingEventExchange() {
        return new TopicExchange(EVENT_EXCHANGE, true, false);
    }

    @Bean
    public Queue bookingEventsQueue() {
        return QueueBuilder.durable(EVENT_QUEUE).build();
    }

    @Bean
    public Binding bookingEventsBinding() {
        return BindingBuilder.bind(bookingEventsQueue())
                .to(bookingEventExchange())
                .with(EVENT_ROUTING_PATTERN);
    }
}
