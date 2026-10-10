package dev.booking.sports.notification.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import dev.booking.sports.notification.config.NotificationRabbitConfig;
import dev.booking.sports.notification.email.EmailMessage;
import dev.booking.sports.notification.email.EmailQueueMessage;
import dev.booking.sports.notification.service.EmailTemplateService.RenderedEmail;
import dev.booking.sports.shared.config.RabbitMQProperties;

@ExtendWith(MockitoExtension.class)
class EmailQueuePublisherTest {

	private static final UUID OUTBOX_EVENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

	@Mock
	private RabbitTemplate rabbitTemplate;

	private EmailQueuePublisher emailQueuePublisher;

	@BeforeEach
	void setUp() {
		emailQueuePublisher = new EmailQueuePublisher(
				rabbitTemplate,
				new RabbitMQProperties(Duration.ofSeconds(5)));
	}

	@Test
	void publish_waitsForBrokerConfirm() {
		EmailQueueMessage message = queueMessage("Subject");

		stubSuccessfulConfirm();

		emailQueuePublisher.publish(message);

		verify(rabbitTemplate).convertAndSend(
				eq(NotificationRabbitConfig.EXCHANGE),
				eq(NotificationRabbitConfig.EMAIL_ROUTING_KEY),
				eq(message),
				any(CorrelationData.class));
	}

	@Test
	void publish_fromRenderedEmail_buildsQueueMessage() {
		RenderedEmail rendered = new RenderedEmail("Hello", "<p>Body</p>");
		stubSuccessfulConfirm();

		emailQueuePublisher.publish(OUTBOX_EVENT_ID, "user@example.com", "Test User", rendered);

		verify(rabbitTemplate).convertAndSend(
				eq(NotificationRabbitConfig.EXCHANGE),
				eq(NotificationRabbitConfig.EMAIL_ROUTING_KEY),
				eq(new EmailQueueMessage(
						OUTBOX_EVENT_ID,
						new EmailMessage("user@example.com", "Test User", "Hello", "<p>Body</p>"))),
				any(CorrelationData.class));
	}

	@Test
	void publish_whenBrokerNacks_wrapsException() {
		EmailQueueMessage message = queueMessage("Subject");
		stubConfirm(new CorrelationData.Confirm(false, "queue unavailable"));

		assertThatThrownBy(() -> emailQueuePublisher.publish(message))
				.isInstanceOf(AmqpException.class)
				.hasMessageContaining(OUTBOX_EVENT_ID.toString());
	}

	@Test
	void publish_whenRabbitFails_wrapsException() {
		EmailQueueMessage message = queueMessage("Subject");
		doThrow(new AmqpException("broker down"))
				.when(rabbitTemplate)
				.convertAndSend(
						eq(NotificationRabbitConfig.EXCHANGE),
						eq(NotificationRabbitConfig.EMAIL_ROUTING_KEY),
						eq(message),
						any(CorrelationData.class));

		assertThatThrownBy(() -> emailQueuePublisher.publish(message))
				.isInstanceOf(AmqpException.class)
				.hasMessageContaining("broker down");
	}

	private EmailQueueMessage queueMessage(String subject) {
		return new EmailQueueMessage(
				OUTBOX_EVENT_ID,
				new EmailMessage("user@example.com", "Test User", subject, "<p>Hi</p>"));
	}

	private void stubSuccessfulConfirm() {
		stubConfirm(new CorrelationData.Confirm(true, "ok"));
	}

	private void stubConfirm(CorrelationData.Confirm confirm) {
		doAnswer(invocation -> {
			CorrelationData correlationData = invocation.getArgument(3);
			correlationData.getFuture().complete(confirm);
			return null;
		}).when(rabbitTemplate)
				.convertAndSend(
						any(String.class),
						any(String.class),
						any(Object.class),
						any(CorrelationData.class));
	}
}
