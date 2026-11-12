package dev.booking.sports.notification.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import dev.booking.sports.shared.messaging.ConfirmedRabbitPublisher;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConversionException;
import dev.booking.sports.notification.config.NotificationRabbitConfig;
import dev.booking.sports.notification.email.EmailDeliveryException;
import dev.booking.sports.shared.config.RabbitMQProperties;

@ExtendWith(MockitoExtension.class)
class EmailRecoveryServiceTest {
    @Mock RabbitTemplate rabbit;
    EmailRecoveryService recovery;

    @BeforeEach void setup() {
        recovery = new EmailRecoveryService(rabbit, new ConfirmedRabbitPublisher(rabbit, new RabbitMQProperties(Duration.ofMillis(25))));
    }

    private void confirm(boolean ack, boolean returned) {
        doAnswer(call -> {
            CorrelationData cd = call.getArgument(3);
            if (returned) cd.setReturned(new ReturnedMessage(call.getArgument(2), 312, "NO_ROUTE", "", ""));
            cd.getFuture().complete(new CorrelationData.Confirm(ack, null));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    @Test void transientFailureSchedulesNextRetryAndPreservesOriginalBodyAndHeaders() {
        confirm(true, false);
        var original = MessageBuilder.withBody("json".getBytes()).setHeader("__TypeId__", "EmailQueueMessage")
                .setHeader(NotificationRabbitConfig.RETRY_HEADER, 1).build();
        recovery.recover(original, new EmailDeliveryException("429"));
        var copy = ArgumentCaptor.forClass(Message.class);
        verify(rabbit).send(eq(""), eq(NotificationRabbitConfig.retryQueue(2)), copy.capture(), any(CorrelationData.class));
        assertThat(copy.getValue().getBody()).containsExactly(original.getBody());
        assertThat(copy.getValue().getMessageProperties().getHeaders())
                .containsAllEntriesOf(Map.of("__TypeId__", "EmailQueueMessage", NotificationRabbitConfig.RETRY_HEADER, 2));
        assertThat(original.getMessageProperties().getHeaders().get(NotificationRabbitConfig.RETRY_HEADER)).isEqualTo(1);
        assertThat(copy.getValue().getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
    }

    @Test void exhaustedRetriesGoToDlq() {
        confirm(true, false);
        var original = MessageBuilder.withBody(new byte[0]).setHeader(NotificationRabbitConfig.RETRY_HEADER, 3).build();
        recovery.recover(original, new IllegalStateException("database unavailable"));
        verify(rabbit).send(eq(NotificationRabbitConfig.DEAD_LETTER_EXCHANGE),
                eq(NotificationRabbitConfig.EMAIL_DLQ_ROUTING_KEY), any(Message.class), any(CorrelationData.class));
    }

    @Test void permanentAndMalformedFailuresGoDirectlyToDlq() {
        confirm(true, false);
        recovery.recover(new Message(new byte[0]), new EmailDeliveryException("invalid recipient", false));
        recovery.recover(new Message(new byte[0]), new MessageConversionException("invalid JSON"));
        verify(rabbit, times(2)).send(eq(NotificationRabbitConfig.DEAD_LETTER_EXCHANGE),
                eq(NotificationRabbitConfig.EMAIL_DLQ_ROUTING_KEY), any(Message.class), any(CorrelationData.class));
    }

    @Test void noRouteWithAckDoesNotCountAsRecovery() {
        confirm(true, true);
        assertThatThrownBy(() -> recovery.recover(new Message(new byte[0]), new IllegalStateException()))
                .isInstanceOf(AmqpException.class);
    }

    @Test void nackDoesNotCountAsRecovery() {
        confirm(false, false);
        assertThatThrownBy(() -> recovery.recover(new Message(new byte[0]), new IllegalStateException()))
                .isInstanceOf(AmqpException.class);
    }

    @Test void missingConfirmDoesNotCountAsRecovery() {
        assertThatThrownBy(() -> recovery.recover(new Message(new byte[0]), new IllegalStateException()))
                .isInstanceOf(AmqpException.class).hasCauseInstanceOf(java.util.concurrent.TimeoutException.class);
    }

    private com.rabbitmq.client.Channel dlqDelivery() throws Exception {
        var channel = mock(com.rabbitmq.client.Channel.class);
        var props = new com.rabbitmq.client.AMQP.BasicProperties.Builder()
                .headers(Map.of(NotificationRabbitConfig.RETRY_HEADER, 3, "__TypeId__", "type", "x-death", "old"))
                .build();
        when(channel.basicGet(NotificationRabbitConfig.EMAIL_DLQ, false)).thenReturn(
                new com.rabbitmq.client.GetResponse(new com.rabbitmq.client.Envelope(7L, false, "", ""),
                        props, "body".getBytes(), 0), null);
        doAnswer(call -> call.<org.springframework.amqp.rabbit.core.ChannelCallback<?>>getArgument(0).doInRabbit(channel))
                .when(rabbit).execute(any(org.springframework.amqp.rabbit.core.ChannelCallback.class));
        return channel;
    }

    @Test void replayResetsRetryHistoryAndAcknowledgesOnlyAfterConfirmedPublish() throws Exception {
        var channel = dlqDelivery();
        confirm(true, false);
        assertThat(recovery.replayDeadLetters(25)).isEqualTo(1);
        var message = ArgumentCaptor.forClass(Message.class);
        var order = inOrder(rabbit, channel);
        order.verify(rabbit).execute(any(org.springframework.amqp.rabbit.core.ChannelCallback.class));
        order.verify(channel).basicGet(NotificationRabbitConfig.EMAIL_DLQ, false);
        order.verify(rabbit).send(eq(NotificationRabbitConfig.EXCHANGE),
                eq(NotificationRabbitConfig.EMAIL_ROUTING_KEY), message.capture(), any(CorrelationData.class));
        order.verify(channel).basicAck(7L, false);
        assertThat(message.getValue().getMessageProperties().getHeaders())
                .containsEntry(NotificationRabbitConfig.RETRY_HEADER, 0)
                .containsEntry("__TypeId__", "type").doesNotContainKey("x-death");
    }

    @Test void failedReplayRetainsDlqDelivery() throws Exception {
        var channel = dlqDelivery();
        confirm(false, false);
        assertThatThrownBy(() -> recovery.replayDeadLetters(1)).isInstanceOf(AmqpException.class);
        verify(channel).basicNack(7L, false, true);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }
}
