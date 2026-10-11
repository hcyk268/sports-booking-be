package dev.booking.sports.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationRabbitConfig {

	public static final String EXCHANGE = "sports.notification";
	public static final String DEAD_LETTER_EXCHANGE = "sports.notification.dlx";

	public static final String EMAIL_QUEUE = "sports.notification.email";
	public static final String EMAIL_DLQ = "sports.notification.email.dlq";

	public static final String EMAIL_ROUTING_KEY = "email.send";
	public static final String EMAIL_DLQ_ROUTING_KEY = "email.dlq";

	@Bean
	public TopicExchange notificationExchange() {
		return new TopicExchange(EXCHANGE, true, false);
	}

	@Bean
	public TopicExchange notificationDeadLetterExchange() {
		return new TopicExchange(DEAD_LETTER_EXCHANGE, true, false);
	}

	@Bean
	public Queue emailQueue() {
		return QueueBuilder.durable(EMAIL_QUEUE)
				.deadLetterExchange(DEAD_LETTER_EXCHANGE)
				.deadLetterRoutingKey(EMAIL_DLQ_ROUTING_KEY)
				.build();
	}

	@Bean
	public Queue emailDeadLetterQueue() {
		return QueueBuilder.durable(EMAIL_DLQ).build();
	}

	@Bean
	public Binding emailQueueBinding() {
		return BindingBuilder.bind(emailQueue()).to(notificationExchange()).with(EMAIL_ROUTING_KEY);
	}

	@Bean
	public Binding emailDeadLetterQueueBinding() {
		return BindingBuilder.bind(emailDeadLetterQueue())
				.to(notificationDeadLetterExchange())
				.with(EMAIL_DLQ_ROUTING_KEY);
	}
}
