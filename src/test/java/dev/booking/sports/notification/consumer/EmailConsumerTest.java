package dev.booking.sports.notification.consumer;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rabbitmq.client.Channel;

import dev.booking.sports.notification.delivery.EmailDeliveryDedupService;
import dev.booking.sports.notification.email.EmailDeliveryException;
import dev.booking.sports.notification.email.EmailMessage;
import dev.booking.sports.notification.email.EmailQueueMessage;
import dev.booking.sports.notification.email.EmailSender;

@ExtendWith(MockitoExtension.class)
class EmailConsumerTest {

	private static final UUID OUTBOX_EVENT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
	private static final long DELIVERY_TAG = 42L;

	@Mock
	private EmailSender emailSender;

	@Mock
	private EmailDeliveryDedupService deliveryDedupService;

	@Mock
	private Channel channel;

	@InjectMocks
	private EmailConsumer emailConsumer;

	@Test
	void consume_success_acknowledgesMessage() throws IOException {
		EmailQueueMessage message = queueMessage();

		when(deliveryDedupService.tryAcquire(OUTBOX_EVENT_ID)).thenReturn(true);

		emailConsumer.consume(message, channel, DELIVERY_TAG);

		verify(emailSender).send(message.email());
		verify(channel).basicAck(DELIVERY_TAG, false);
		verify(deliveryDedupService, never()).release(OUTBOX_EVENT_ID);
		verifyNoMoreInteractions(channel);
	}

	@Test
	void consume_duplicateDelivery_skipsSendAndAcknowledges() throws IOException {
		EmailQueueMessage message = queueMessage();

		when(deliveryDedupService.tryAcquire(OUTBOX_EVENT_ID)).thenReturn(false);

		emailConsumer.consume(message, channel, DELIVERY_TAG);

		verify(emailSender, never()).send(message.email());
		verify(channel).basicAck(DELIVERY_TAG, false);
		verifyNoMoreInteractions(channel);
	}

	@Test
	void consume_deliveryFailure_releasesDedupAndNacksWithoutRequeue() throws IOException {
		EmailQueueMessage message = queueMessage();

		when(deliveryDedupService.tryAcquire(OUTBOX_EVENT_ID)).thenReturn(true);
		doThrow(new EmailDeliveryException("SendGrid rejected"))
				.when(emailSender)
				.send(message.email());

		emailConsumer.consume(message, channel, DELIVERY_TAG);

		verify(deliveryDedupService).release(OUTBOX_EVENT_ID);
		verify(channel).basicNack(DELIVERY_TAG, false, false);
		verifyNoMoreInteractions(channel);
	}

	private EmailQueueMessage queueMessage() {
		return new EmailQueueMessage(
				OUTBOX_EVENT_ID,
				new EmailMessage("user@example.com", "Test User", "Subject", "<p>Hi</p>"));
	}
}
