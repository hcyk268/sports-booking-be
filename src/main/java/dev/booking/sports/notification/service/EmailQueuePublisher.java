package dev.booking.sports.notification.service;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import dev.booking.sports.notification.config.NotificationRabbitConfig;
import dev.booking.sports.notification.email.EmailMessage;
import dev.booking.sports.notification.service.EmailTemplateService.RenderedEmail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailQueuePublisher {

	private final RabbitTemplate rabbitTemplate;

	public void publish(String to, String toName, RenderedEmail email) {
		publish(new EmailMessage(to, toName, email.subject(), email.htmlBody()));
	}

	public void publish(EmailMessage message) {
		try {
			rabbitTemplate.convertAndSend(
					NotificationRabbitConfig.EXCHANGE,
					NotificationRabbitConfig.EMAIL_ROUTING_KEY,
					message);
		}
		catch (AmqpException exception) {
			throw new IllegalStateException("Could not queue email '" + message.subject() + "'", exception);
		}
	}
}
