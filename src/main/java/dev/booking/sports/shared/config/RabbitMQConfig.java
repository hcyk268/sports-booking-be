package dev.booking.sports.shared.config;

import org.springframework.amqp.rabbit.connection.ConnectionNameStrategy;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class RabbitMQConfig {

	@Bean
	public JacksonJsonMessageConverter rabbitMessageConverter() {
		DefaultClassMapper classMapper = new DefaultClassMapper();
		classMapper.setTrustedPackages("dev.booking.sports.notification.email");

		JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
		converter.setClassMapper(classMapper);

		return converter;
	}

	@Bean
	public ConnectionNameStrategy rabbitConnectionName() {
		return connectionFactory -> "sports-booking-api";
	}

	@Bean
	public RabbitTemplateCustomizer rabbitTemplateCustomizer() {
		return template -> {
			template.setMandatory(true);
			template.setReturnsCallback(returned ->
					log.error(
							"Unroutable RabbitMQ message: exchange={}, routingKey={}, replyCode={}, replyText={}",
							returned.getExchange(),
							returned.getRoutingKey(),
							returned.getReplyCode(),
							returned.getReplyText()));
		};
	}
}
