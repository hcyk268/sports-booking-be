package dev.booking.sports.payment.infrastructure.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.listener.adapter.MessagingMessageListenerAdapter;
import org.springframework.amqp.listener.adapter.HandlerAdapter;
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory;
import org.springframework.transaction.TransactionSystemException;

import com.rabbitmq.client.Channel;
import dev.booking.sports.booking.domain.event.BookingCancelledEvent;
import dev.booking.sports.payment.application.BookingPaymentCancellationService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

class BookingCancellationConsumerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final BookingPaymentCancellationService service = mock(BookingPaymentCancellationService.class);
    private final BookingCancellationRecovery recovery = mock(BookingCancellationRecovery.class);
    private final Channel channel = mock(Channel.class);
    private final BookingCancellationConsumer consumer = new BookingCancellationConsumer(mapper, service, recovery);
    private final BookingCancelledEvent event = new BookingCancelledEvent(UUID.randomUUID(), "BK-test",
            UUID.randomUUID(), new BigDecimal("50000"), "VND", UUID.randomUUID(), "Change of plans",
            Instant.now(), List.of());

    private Message message() {
        return new Message(mapper.writeValueAsBytes(new BookingCancellationMessage(UUID.randomUUID(), "BOOKING",
                event.bookingId().toString(), "booking.cancelled", 1, event)));
    }

    @Test
    void acknowledgesOnlyAfterServiceReturns() throws Exception {
        consumer.consume(message(), channel, 42);
        var order = inOrder(service, channel);
        order.verify(service).cancelPendingPayment(event);
        order.verify(channel).basicAck(42, false);
        verifyNoInteractions(recovery);
    }

    @Test
    void commitFailureIsRecoveredBeforeAck() throws Exception {
        var failure = new TransactionSystemException("Commit failed");
        doThrow(failure).when(service).cancelPendingPayment(event);
        var raw = message();
        consumer.consume(raw, channel, 42);
        var order = inOrder(recovery, channel);
        order.verify(recovery).recover(raw, failure);
        order.verify(channel).basicAck(42, false);
    }

    @Test
    void malformedJsonAndUntrustedTypeHeaderReachRecovery() throws Exception {
        var method = BookingCancellationConsumer.class.getMethod("consume", Message.class, Channel.class, long.class);
        var adapter = new MessagingMessageListenerAdapter(consumer, method);
        adapter.setMessageConverter(new BookingCancellationRabbitConfig().rawPaymentCancellationMessageConverter());
        var factory = new DefaultMessageHandlerMethodFactory();
        factory.afterPropertiesSet();
        adapter.setHandlerAdapter(new HandlerAdapter(factory.createInvocableHandlerMethod(consumer, method)));
        var raw = new Message("{invalid".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        raw.getMessageProperties().setHeader("__TypeId__", "untrusted.ProducerClass");
        raw.getMessageProperties().setDeliveryTag(42);
        adapter.onMessage(raw, channel);
        verifyNoInteractions(service);
        verify(recovery).recover(eq(raw), any(JacksonException.class));
        verify(channel).basicAck(42, false);
    }

    @Test
    void rejectsMismatchedEnvelope() {
        assertThatThrownBy(() -> new BookingCancellationMessage(UUID.randomUUID(), "BOOKING",
                UUID.randomUUID().toString(), "booking.cancelled", 1, event))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failedRecoveryRequeuesWithoutAck() throws Exception {
        doThrow(new IllegalStateException("Database unavailable")).when(service).cancelPendingPayment(event);
        doThrow(new IllegalStateException("Broker unavailable")).when(recovery).recover(any(), any());
        consumer.consume(message(), channel, 42);
        verify(channel).basicNack(42, false, true);
        verifyNoMoreInteractions(channel);
    }

    @Test
    void ackFailureDoesNotRepublishCommittedWork() throws Exception {
        doThrow(new IOException("Channel closed")).when(channel).basicAck(42, false);
        assertThatThrownBy(() -> consumer.consume(message(), channel, 42)).isInstanceOf(IOException.class);
        verify(service).cancelPendingPayment(event);
        verifyNoInteractions(recovery);
    }
}
