package dev.booking.sports.notification.consumer;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rabbitmq.client.Channel;

import dev.booking.sports.notification.email.EmailDeliveryException;
import dev.booking.sports.notification.email.EmailMessage;
import dev.booking.sports.notification.email.EmailSender;

@ExtendWith(MockitoExtension.class)
class EmailConsumerTest {

	private static final long DELIVERY_TAG = 42L;

	@Mock
	private EmailSender emailSender;

	@Mock
	private Channel channel;

	@InjectMocks
	private EmailConsumer emailConsumer;

	@Test
	void consume_success_acknowledgesMessage() throws IOException {
		EmailMessage message = new EmailMessage("user@example.com", "Test User", "Subject", "<p>Hi</p>");

		emailConsumer.consume(message, channel, DELIVERY_TAG);

		verify(emailSender).send(message);
		verify(channel).basicAck(DELIVERY_TAG, false);
		verifyNoMoreInteractions(channel);
	}

	@Test
	void consume_deliveryFailure_nacksWithoutRequeue() throws IOException {
		EmailMessage message = new EmailMessage("user@example.com", "Test User", "Subject", "<p>Hi</p>");
		doThrow(new EmailDeliveryException("SendGrid rejected"))
				.when(emailSender)
				.send(message);

		emailConsumer.consume(message, channel, DELIVERY_TAG);

		verify(channel).basicNack(DELIVERY_TAG, false, false);
		verifyNoMoreInteractions(channel);
	}
}
