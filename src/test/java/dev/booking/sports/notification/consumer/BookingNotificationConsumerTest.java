package dev.booking.sports.notification.consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.listener.adapter.MessagingMessageListenerAdapter;
import org.springframework.amqp.listener.adapter.HandlerAdapter;
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory;
import org.springframework.transaction.TransactionSystemException;

import com.rabbitmq.client.Channel;
import dev.booking.sports.notification.BookingNotificationFixtures;
import dev.booking.sports.notification.application.BookingNotificationHandler;
import dev.booking.sports.notification.config.NotificationRabbitConfig;
import dev.booking.sports.notification.service.BookingNotificationRecoveryService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

class BookingNotificationConsumerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final BookingNotificationHandler handler = mock(BookingNotificationHandler.class);
    private final BookingNotificationRecoveryService recovery = mock(BookingNotificationRecoveryService.class);
    private final Channel channel = mock(Channel.class);
    private final BookingNotificationConsumer consumer = new BookingNotificationConsumer(mapper, handler, recovery);
    private BookingNotificationMessage event;
    private Message raw;

    @BeforeEach void setup() {
        event = BookingNotificationFixtures.message(UUID.randomUUID(), "booking.confirmed");
        raw = new Message(mapper.writeValueAsBytes(event));
        // Compare the decoded wire representation, including JSON numeric node types.
        event = mapper.readValue(raw.getBody(), BookingNotificationMessage.class);
    }

    @Test void acknowledgesOnlyAfterHandlerReturnsFromCommit() throws Exception {
        consumer.consume(raw, channel, 42);
        var order = inOrder(handler, channel);
        order.verify(handler).handle(event);
        order.verify(channel).basicAck(42, false);
        verifyNoInteractions(recovery);
    }

    @Test void commitFailureIsRecoveredBeforeAcknowledging() throws Exception {
        var failure = new TransactionSystemException("Commit failed");
        doThrow(failure).when(handler).handle(event);
        consumer.consume(raw, channel, 42);
        var order = inOrder(handler, recovery, channel);
        order.verify(handler).handle(event);
        order.verify(recovery).recover(raw, failure);
        order.verify(channel).basicAck(42, false);
    }

    @Test void malformedJsonReachesRecoveryThroughListenerAdapterDespiteTypeHeaders() throws Exception {
        var method = BookingNotificationConsumer.class.getMethod("consume", Message.class, Channel.class, long.class);
        var adapter = new MessagingMessageListenerAdapter(consumer, method);
        adapter.setMessageConverter(new NotificationRabbitConfig().rawEmailMessageConverter());
        var factory = new DefaultMessageHandlerMethodFactory();
        factory.afterPropertiesSet();
        adapter.setHandlerAdapter(new HandlerAdapter(factory.createInvocableHandlerMethod(consumer, method)));
        var invalid = new Message("{invalid".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        invalid.getMessageProperties().setHeader("__TypeId__", "untrusted.ProducerClass");
        invalid.getMessageProperties().setDeliveryTag(42);
        adapter.onMessage(invalid, channel);
        verifyNoInteractions(handler);
        var order = inOrder(recovery, channel);
        order.verify(recovery).recover(eq(invalid), any(JacksonException.class));
        order.verify(channel).basicAck(42, false);
    }

    @Test void failedRecoveryRequeuesOriginalWithoutAcknowledging() throws Exception {
        doThrow(new IllegalStateException("Database down")).when(handler).handle(event);
        doThrow(new IllegalStateException("Broker down")).when(recovery).recover(eq(raw), any());
        consumer.consume(raw, channel, 42);
        verify(channel).basicNack(42, false, true);
        verifyNoMoreInteractions(channel);
    }

    @Test void ackFailureDoesNotRetryAnAlreadyCommittedNotification() throws Exception {
        doThrow(new IOException("Channel closed")).when(channel).basicAck(42, false);
        assertThatThrownBy(() -> consumer.consume(raw, channel, 42)).isInstanceOf(IOException.class);
        verify(handler).handle(event);
        verifyNoInteractions(recovery);
        verify(channel).basicAck(42, false);
        verifyNoMoreInteractions(channel);
    }
}
