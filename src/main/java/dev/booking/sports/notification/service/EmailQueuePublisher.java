package dev.booking.sports.notification.service;

import java.util.UUID;
import org.springframework.stereotype.Component;
import dev.booking.sports.notification.config.NotificationRabbitConfig;
import dev.booking.sports.notification.email.EmailMessage;
import dev.booking.sports.notification.email.EmailQueueMessage;
import dev.booking.sports.notification.service.EmailTemplateService.RenderedEmail;
import dev.booking.sports.shared.messaging.ConfirmedRabbitPublisher;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class EmailQueuePublisher {
    private final ConfirmedRabbitPublisher publisher;

	public void publish(UUID outboxEventId, String to, String toName, RenderedEmail email) {
		publish(new EmailQueueMessage(
				outboxEventId,
				new EmailMessage(to, toName, email.subject(), email.htmlBody())));
	}

    public void publish(EmailQueueMessage message) {
        publisher.publish(NotificationRabbitConfig.EXCHANGE, NotificationRabbitConfig.EMAIL_ROUTING_KEY,
                message, message.outboxEventId().toString());
    }
}
