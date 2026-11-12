package dev.booking.sports.notification.consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConversionException;
import dev.booking.sports.notification.service.EmailRecoveryService;
import dev.booking.sports.notification.delivery.EmailDeliveryDedupService;
import dev.booking.sports.notification.email.*;

@ExtendWith(MockitoExtension.class)
class EmailConsumerTest {
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final long TAG = 42L;
    private final EmailQueueMessage message = new EmailQueueMessage(EVENT_ID,
            new EmailMessage("user@example.com", "Test", "Subject", "<p>Hi</p>"));
    @Mock private EmailSender sender;
    @Mock private EmailDeliveryDedupService dedup;
    @Mock private Channel channel;
    @Mock private JacksonJsonMessageConverter converter;
    @Mock private EmailRecoveryService recovery;
    private final Message raw = new Message(new byte[0]);
    private EmailConsumer consumer;

    @BeforeEach void setUp() {
        consumer = new EmailConsumer(sender, dedup, converter, recovery);
        lenient().when(converter.fromMessage(raw)).thenReturn(message);
    }

    @Test void successSendsInsideTransactionAndAcknowledgesAfterReturn() throws IOException {
        when(dedup.deliverOnce(eq(EVENT_ID), any(Runnable.class))).thenAnswer(call -> {
            verifyNoInteractions(channel);
            call.<Runnable>getArgument(1).run();
            verifyNoInteractions(channel);
            return true;
        });
        consumer.consume(raw, channel, TAG);
        var order = inOrder(dedup, sender, channel);
        order.verify(dedup).deliverOnce(eq(EVENT_ID), any(Runnable.class));
        order.verify(sender).send(message.email());
        order.verify(channel).basicAck(TAG, false);
        verifyNoMoreInteractions(channel);
    }

    @Test void completedDeliverySkipsSendAndAcknowledges() throws IOException {
        when(dedup.deliverOnce(eq(EVENT_ID), any(Runnable.class))).thenReturn(false);
        consumer.consume(raw, channel, TAG);
        verifyNoInteractions(sender);
        verify(channel).basicAck(TAG, false);
        verifyNoMoreInteractions(channel);
    }

    @Test void sendFailureDeadLettersAfterTransactionRollsBack() throws IOException {
        when(dedup.deliverOnce(eq(EVENT_ID), any(Runnable.class)))
                .thenThrow(new EmailDeliveryException("SendGrid rejected"));
        consumer.consume(raw, channel, TAG);
        verify(recovery).recover(eq(raw), any(EmailDeliveryException.class));
        verify(channel).basicAck(TAG, false);
        verifyNoMoreInteractions(channel);
    }

    @Test void unexpectedFailureIsForwardedBeforeAcknowledging() throws IOException {
        when(dedup.deliverOnce(eq(EVENT_ID), any(Runnable.class)))
                .thenThrow(new IllegalStateException("unexpected failure"));
        consumer.consume(raw, channel, TAG);
        verify(recovery).recover(eq(raw), any(RuntimeException.class));
        verify(channel).basicAck(TAG, false);
        verifyNoMoreInteractions(channel);
    }

    @Test void commitFailureIsForwardedBeforeAcknowledging() throws IOException {
        when(dedup.deliverOnce(eq(EVENT_ID), any(Runnable.class)))
                .thenThrow(new org.springframework.transaction.TransactionSystemException("commit failed"));
        consumer.consume(raw, channel, TAG);
        verify(recovery).recover(eq(raw), any(RuntimeException.class));
        verify(channel).basicAck(TAG, false);
        verifyNoMoreInteractions(channel);
    }

    @Test void ackFailurePropagatesWithoutNackingCompletedDelivery() throws IOException {
        when(dedup.deliverOnce(eq(EVENT_ID), any(Runnable.class))).thenReturn(true);
        doThrow(new IOException("channel closed")).when(channel).basicAck(TAG, false);
        assertThatThrownBy(() -> consumer.consume(raw, channel, TAG)).isInstanceOf(IOException.class);
        verify(channel).basicAck(TAG, false);
        verifyNoMoreInteractions(channel);
    }
    @Test void malformedMessageUsesConfirmedDlqPath() throws IOException {
        when(converter.fromMessage(raw)).thenThrow(new MessageConversionException("invalid json"));
        consumer.consume(raw, channel, TAG);
        verifyNoInteractions(sender, dedup);
        var order = inOrder(recovery, channel);
        order.verify(recovery).recover(eq(raw), any(MessageConversionException.class));
        order.verify(channel).basicAck(TAG, false);
    }

    @Test void failedRecoveryRetainsOriginalMessage() throws IOException {
        when(dedup.deliverOnce(eq(EVENT_ID), any(Runnable.class))).thenThrow(new IllegalStateException("send failed"));
        doThrow(new IllegalStateException("broker nack")).when(recovery).recover(eq(raw), any(RuntimeException.class));
        consumer.consume(raw, channel, TAG);
        verify(channel).basicNack(TAG, false, true);
        verifyNoMoreInteractions(channel);
    }
}
