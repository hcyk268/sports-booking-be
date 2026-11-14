package dev.booking.sports.notification.config;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationRabbitConfig {
	@Bean({ "rawEmailMessageConverter", "rawNotificationMessageConverter" })
	public org.springframework.amqp.support.converter.MessageConverter rawEmailMessageConverter() {
		// The listener owns decoding, including failures; do not deserialize in the adapter.
		return new org.springframework.amqp.support.converter.MessageConverter() {
			@Override
			public Object fromMessage(org.springframework.amqp.core.Message message) {
				return message.getBody();
			}

			@Override
			public org.springframework.amqp.core.Message toMessage(Object value,
					org.springframework.amqp.core.MessageProperties properties) {
				throw new UnsupportedOperationException("Receive-only notification converter");
			}
		};
	}

	public static final String EXCHANGE = "sports.notification";
	public static final String DEAD_LETTER_EXCHANGE = "sports.notification.dlx";

	public static final String EMAIL_QUEUE = "sports.notification.email";
	public static final String EMAIL_DLQ = "sports.notification.email.dlq";

	public static final String EMAIL_ROUTING_KEY = "email.send";
	public static final String EMAIL_DLQ_ROUTING_KEY = "email.dlq";
	public static final String RETRY_HEADER = "x-email-retry-count";
	private static final java.util.List<Integer> RETRY_DELAYS_MS = java.util.List.of(10_000, 60_000, 300_000);
	public static final int MAX_RETRIES = RETRY_DELAYS_MS.size();

	public static String retryQueue(int attempt) {
		return EMAIL_QUEUE + ".retry." + attempt;
	}

    @Bean
    public Declarables emailTopology() {
        var exchange = new TopicExchange(EXCHANGE, true, false);
        var dlx = new TopicExchange(DEAD_LETTER_EXCHANGE, true, false);
        var queue = QueueBuilder.durable(EMAIL_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE).deadLetterRoutingKey(EMAIL_DLQ_ROUTING_KEY).build();
        var dlq = QueueBuilder.durable(EMAIL_DLQ).build();
        var topology = new java.util.ArrayList<org.springframework.amqp.core.Declarable>(java.util.List.of(
                exchange, dlx, queue, dlq,
                BindingBuilder.bind(queue).to(exchange).with(EMAIL_ROUTING_KEY),
                BindingBuilder.bind(dlq).to(dlx).with(EMAIL_DLQ_ROUTING_KEY)));
        for (int i = 0; i < MAX_RETRIES; i++) {
            topology.add(QueueBuilder.durable(retryQueue(i + 1)).quorum()
                    .ttl(RETRY_DELAYS_MS.get(i)).maxLengthBytes(67_108_864)
                    .overflow(QueueBuilder.Overflow.rejectPublish)
                    .deadLetterExchange(EXCHANGE).deadLetterRoutingKey(EMAIL_ROUTING_KEY)
                    .withArgument("x-dead-letter-strategy", "at-least-once").build());
        }
        return new Declarables(topology);
    }
}
