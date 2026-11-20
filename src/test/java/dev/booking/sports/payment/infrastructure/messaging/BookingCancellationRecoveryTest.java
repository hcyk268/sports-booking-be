package dev.booking.sports.payment.infrastructure.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;

import dev.booking.sports.shared.messaging.ConfirmedRabbitPublisher;

class BookingCancellationRecoveryTest {
    private final ConfirmedRabbitPublisher publisher = mock(ConfirmedRabbitPublisher.class);
    private final BookingCancellationRecovery recovery = new BookingCancellationRecovery(publisher);

    @Test
    void transientFailureUsesDelayedRetryAndPreservesOriginal() {
        var raw = new Message(new byte[] { 1, 2 });
        var copy = ArgumentCaptor.forClass(Message.class);
        recovery.recover(raw, new IllegalStateException("Database unavailable"));
        verify(publisher).publish(eq(""), eq(BookingCancellationRabbitConfig.retryQueue(1)), copy.capture());
        assertThat(copy.getValue().getBody()).containsExactly(1, 2);
        assertThat((Object) copy.getValue().getMessageProperties().getHeader(BookingCancellationRabbitConfig.RETRY_HEADER))
                .isEqualTo(1);
        assertThat(raw.getMessageProperties().getHeaders()).doesNotContainKey(BookingCancellationRabbitConfig.RETRY_HEADER);
    }

    @Test
    void permanentAndExhaustedFailuresGoToDlq() {
        var raw = new Message(new byte[0]);
        recovery.recover(raw, new IllegalArgumentException("Invalid event"));
        raw.getMessageProperties().setHeader(BookingCancellationRabbitConfig.RETRY_HEADER,
                BookingCancellationRabbitConfig.MAX_RETRIES);
        recovery.recover(raw, new IllegalStateException("Database unavailable"));
        verify(publisher, times(2)).publish(eq(""), eq(BookingCancellationRabbitConfig.DLQ), any(Message.class));
    }

    @Test
    void unconfirmedPublicationPropagatesToRetainOriginalDelivery() {
        doThrow(new IllegalStateException("No publisher confirm")).when(publisher)
                .publish(anyString(), anyString(), any(Message.class));
        assertThatThrownBy(() -> recovery.recover(new Message(new byte[0]), new IllegalStateException()))
                .isInstanceOf(IllegalStateException.class);
    }
}
