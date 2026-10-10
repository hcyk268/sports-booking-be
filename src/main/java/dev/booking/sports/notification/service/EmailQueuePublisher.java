package dev.booking.sports.notification.service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import dev.booking.sports.notification.config.NotificationRabbitConfig;
import dev.booking.sports.notification.email.EmailMessage;
import dev.booking.sports.notification.email.EmailQueueMessage;
import dev.booking.sports.notification.service.EmailTemplateService.RenderedEmail;
import dev.booking.sports.shared.config.RabbitMQProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailQueuePublisher {

	private final RabbitTemplate rabbitTemplate;
	private final RabbitMQProperties rabbitMQProperties;

	public void publish(UUID outboxEventId, String to, String toName, RenderedEmail email) {
		publish(new EmailQueueMessage(
				outboxEventId,
				new EmailMessage(to, toName, email.subject(), email.htmlBody())));
	}

	public void publish(EmailQueueMessage message) {
		CorrelationData correlationData = new CorrelationData(message.outboxEventId().toString());

		try {
			rabbitTemplate.convertAndSend(
					NotificationRabbitConfig.EXCHANGE,
					NotificationRabbitConfig.EMAIL_ROUTING_KEY,
					message,
					correlationData);

			CorrelationData.Confirm confirm = correlationData.getFuture()
					.get(rabbitMQProperties.publisherConfirmTimeout().toMillis(), TimeUnit.MILLISECONDS);

			if (confirm == null || !confirm.isAck()) {
				String reason = confirm == null ? "no confirm received" : confirm.getReason();
				throw new AmqpException("Broker nacked publish for outbox event "
						+ message.outboxEventId()
						+ ": "
						+ reason);
			}
		}
		catch (AmqpException exception) {
			throw exception;
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(
					"Interrupted while waiting for publish confirm on outbox event " + message.outboxEventId(),
					exception);
		}
		catch (TimeoutException exception) {
			throw new IllegalStateException(
					"Timed out waiting for publish confirm on outbox event " + message.outboxEventId(),
					exception);
		}
		catch (Exception exception) {
			throw new IllegalStateException(
					"Could not queue email '" + message.email().subject() + "' for outbox event "
							+ message.outboxEventId(),
					exception);
		}
	}
}
