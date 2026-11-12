package dev.booking.sports.payment.infrastructure.messaging;

import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentRabbitConfig {

    public static final String EXCHANGE = "sports.payment";
    public static final String EVENT_QUEUE = "sports.payment.events";
    public static final String EVENT_ROUTING_PATTERN = "payment.#";

    @Bean
    public Declarables paymentTopology() {
        var exchange = new TopicExchange(EXCHANGE, true, false);
        var queue = QueueBuilder.durable(EVENT_QUEUE).build();
        return new Declarables(exchange, queue,
                BindingBuilder.bind(queue).to(exchange).with(EVENT_ROUTING_PATTERN));
    }
}
