package dev.booking.sports.notification.consumer;

import java.io.IOException;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

import dev.booking.sports.notification.config.NotificationRabbitConfig;
import dev.booking.sports.notification.email.EmailDeliveryException;
import dev.booking.sports.notification.email.EmailMessage;
import dev.booking.sports.notification.email.EmailSender;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Component
@RequiredArgsConstructor
public class EmailConsumer {

	private final EmailSender emailSender;

	@RabbitListener(queues = NotificationRabbitConfig.EMAIL_QUEUE)
	public void consume(
			EmailMessage message,
			Channel channel,
			@Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

		try {
			emailSender.send(message);
			channel.basicAck(deliveryTag, false);
		}
		catch (EmailDeliveryException exception) {
			log.error("Dead-lettering email '{}' to {}", message.subject(), message.to(), exception);
			channel.basicNack(deliveryTag, false, false);
		}
	}
}
