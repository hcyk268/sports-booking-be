package dev.booking.sports.booking.infrastructure.messaging;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BookingRabbitConfig {

    public static final String EVENT_EXCHANGE = "sports.booking";

    @Bean
    public TopicExchange bookingEventExchange() {
        return new TopicExchange(EVENT_EXCHANGE, true, false);
    }
}
