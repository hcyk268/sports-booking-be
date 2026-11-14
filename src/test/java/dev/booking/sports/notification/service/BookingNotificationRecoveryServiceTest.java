package dev.booking.sports.notification.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;

import dev.booking.sports.notification.config.BookingNotificationRabbitConfig;
import dev.booking.sports.notification.config.NotificationRabbitConfig;
import dev.booking.sports.shared.messaging.ConfirmedRabbitPublisher;
import tools.jackson.databind.ObjectMapper;

class BookingNotificationRecoveryServiceTest {
    private final ConfirmedRabbitPublisher publisher = mock(ConfirmedRabbitPublisher.class);
    private final BookingNotificationRecoveryService recovery = new BookingNotificationRecoveryService(publisher);

    @ParameterizedTest @ValueSource(ints = { 0, 1, 2 })
    void transientFailureUsesNextRetryQueueAndPersistentUnchangedBody(int previousAttempts) {
        var original = new Message(new byte[] {1, 2, 3});
        original.getMessageProperties().setHeader(BookingNotificationRabbitConfig.RETRY_HEADER, previousAttempts);
        recovery.recover(original, new IllegalStateException("Database unavailable"));
        var copy = ArgumentCaptor.forClass(Message.class);
        verify(publisher).publish(eq(""), eq(BookingNotificationRabbitConfig.retryQueue(previousAttempts + 1)), copy.capture());
        assertThat(copy.getValue().getBody()).containsExactly(original.getBody());
        assertThat(copy.getValue().getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(copy.getValue().getMessageProperties().getHeaders())
                .containsEntry(BookingNotificationRabbitConfig.RETRY_HEADER, previousAttempts + 1);
        assertThat(original.getMessageProperties().getHeaders())
                .containsEntry(BookingNotificationRabbitConfig.RETRY_HEADER, previousAttempts);
    }

    @Test void exhaustedRetriesAndPermanentFailuresGoToDedicatedDlq() {
        var original = new Message(new byte[0]);
        original.getMessageProperties().setHeader(BookingNotificationRabbitConfig.RETRY_HEADER, 3);
        recovery.recover(original, new IllegalStateException("Database unavailable"));
        recovery.recover(new Message(new byte[0]), new IllegalArgumentException("Unsupported version"));
        RuntimeException malformed = catchRuntimeException(() -> new ObjectMapper().readTree("{invalid"));
        recovery.recover(new Message(new byte[0]), malformed);
        verify(publisher, times(3)).publish(eq(NotificationRabbitConfig.DEAD_LETTER_EXCHANGE),
                eq(BookingNotificationRabbitConfig.DLQ_ROUTING_KEY), any(Message.class));
    }

    @Test void unconfirmedForwardingPropagatesSoSourceIsNotAcknowledged() {
        doThrow(new IllegalStateException("Broker nack")).when(publisher).publish(anyString(), anyString(), any(Message.class));
        assertThatThrownBy(() -> recovery.recover(new Message(new byte[0]), new IllegalStateException("Database unavailable")))
                .isInstanceOf(IllegalStateException.class).hasMessage("Broker nack");
    }
}
