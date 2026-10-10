package dev.booking.sports.notification.consumer;

import java.io.IOException;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

import dev.booking.sports.notification.config.NotificationRabbitConfig;
import dev.booking.sports.notification.delivery.EmailDeliveryDedupService;
import dev.booking.sports.notification.email.EmailDeliveryException;
import dev.booking.sports.notification.email.EmailQueueMessage;
import dev.booking.sports.notification.email.EmailSender;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailConsumer {

	private final EmailSender emailSender;
	private final EmailDeliveryDedupService deliveryDedupService;

	@RabbitListener(queues = NotificationRabbitConfig.EMAIL_QUEUE)
	public void consume(
			EmailQueueMessage message,
			Channel channel,
			@Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

		if (!deliveryDedupService.tryAcquire(message.outboxEventId())) {
			log.info(
					"Skipping duplicate email delivery for outbox event {} ({})",
					message.outboxEventId(),
					message.email().subject());
			channel.basicAck(deliveryTag, false);
			return;
		}

		try {
			emailSender.send(message.email());
			channel.basicAck(deliveryTag, false);
		}
		catch (EmailDeliveryException exception) {
			deliveryDedupService.release(message.outboxEventId());
			log.error(
					"Dead-lettering email '{}' to {} for outbox event {}",
					message.email().subject(),
					message.email().to(),
					message.outboxEventId(),
					exception);
			channel.basicNack(deliveryTag, false, false);
		}
	}
}
