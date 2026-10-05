package dev.booking.sports.shared.config;

import org.springframework.amqp.rabbit.connection.ConnectionNameStrategy;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

	@Bean
	public JacksonJsonMessageConverter rabbitMessageConverter() {
		DefaultClassMapper classMapper = new DefaultClassMapper();
		classMapper.setTrustedPackages("dev.booking.sports");

		JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
		converter.setClassMapper(classMapper);

		return converter;
	}

	@Bean
	public ConnectionNameStrategy rabbitConnectionName() {
		return connectionFactory -> "sports-booking-api";
	}
}
