package dev.booking.sports.notification.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import dev.booking.sports.notification.config.NotificationRabbitConfig;
import dev.booking.sports.notification.email.EmailMessage;
import dev.booking.sports.notification.service.EmailTemplateService.RenderedEmail;

@ExtendWith(MockitoExtension.class)
class EmailQueuePublisherTest {

	@Mock
	private RabbitTemplate rabbitTemplate;

	@InjectMocks
	private EmailQueuePublisher emailQueuePublisher;

	@Test
	void publish_sendsToNotificationExchange() {
		EmailMessage message = new EmailMessage("user@example.com", "Test User", "Subject", "<p>Hi</p>");

		emailQueuePublisher.publish(message);

		verify(rabbitTemplate).convertAndSend(
				eq(NotificationRabbitConfig.EXCHANGE),
				eq(NotificationRabbitConfig.EMAIL_ROUTING_KEY),
				eq(message));
	}

	@Test
	void publish_fromRenderedEmail_buildsMessage() {
		RenderedEmail rendered = new RenderedEmail("Hello", "<p>Body</p>");

		emailQueuePublisher.publish("user@example.com", "Test User", rendered);

		verify(rabbitTemplate).convertAndSend(
				eq(NotificationRabbitConfig.EXCHANGE),
				eq(NotificationRabbitConfig.EMAIL_ROUTING_KEY),
				eq(new EmailMessage("user@example.com", "Test User", "Hello", "<p>Body</p>")));
	}

	@Test
	void publish_whenRabbitFails_wrapsException() {
		EmailMessage message = new EmailMessage("user@example.com", "Test User", "Subject", "<p>Hi</p>");
		doThrow(new AmqpException("broker down"))
				.when(rabbitTemplate)
				.convertAndSend(
						NotificationRabbitConfig.EXCHANGE,
						NotificationRabbitConfig.EMAIL_ROUTING_KEY,
						message);

		assertThatThrownBy(() -> emailQueuePublisher.publish(message))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Could not queue email 'Subject'")
				.hasCauseInstanceOf(AmqpException.class);
	}
}
