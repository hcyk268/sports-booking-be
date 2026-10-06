package dev.booking.sports.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import dev.booking.sports.notification.config.NotificationRabbitConfig;
import dev.booking.sports.notification.email.EmailMessage;
import dev.booking.sports.support.BaseIntegrationTest;
import dev.booking.sports.support.IntegrationTest;

@IntegrationTest
class EmailQueuePublisherIntegrationTest extends BaseIntegrationTest {

	@Autowired
	private EmailQueuePublisher emailQueuePublisher;

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@BeforeEach
	void drainQueue() {
		while (rabbitTemplate.receiveAndConvert(NotificationRabbitConfig.EMAIL_QUEUE, 100) != null) {
			// discard leftovers from other tests
		}
	}

	@Test
	void publish_deliversEmailMessageToQueue() {
		EmailMessage outgoing = new EmailMessage(
				"queue-it@example.com",
				"Queue IT",
				"Integration subject",
				"<p>integration body</p>");

		emailQueuePublisher.publish(outgoing);

		Object received = rabbitTemplate.receiveAndConvert(
				NotificationRabbitConfig.EMAIL_QUEUE,
				Duration.ofSeconds(5).toMillis());

		assertThat(received).isInstanceOf(EmailMessage.class);
		EmailMessage email = (EmailMessage) received;
		assertThat(email.to()).isEqualTo("queue-it@example.com");
		assertThat(email.toName()).isEqualTo("Queue IT");
		assertThat(email.subject()).isEqualTo("Integration subject");
		assertThat(email.htmlBody()).contains("integration body");
	}
}
