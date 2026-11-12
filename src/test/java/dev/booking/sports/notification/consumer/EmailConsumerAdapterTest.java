package dev.booking.sports.notification.consumer;

import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.listener.adapter.MessagingMessageListenerAdapter;
import org.springframework.amqp.listener.adapter.HandlerAdapter;
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory;
import com.rabbitmq.client.Channel;
import dev.booking.sports.notification.config.NotificationRabbitConfig;
import dev.booking.sports.notification.delivery.EmailDeliveryDedupService;
import dev.booking.sports.notification.email.EmailSender;
import dev.booking.sports.notification.service.EmailRecoveryService;
import dev.booking.sports.shared.config.RabbitMQConfig;

class EmailConsumerAdapterTest {
    @Test void malformedJsonReachesRecoveryInsteadOfBeingRejectedByFrameworkBeforeListener() throws Exception {
        var sender = mock(EmailSender.class);
        var dedup = mock(EmailDeliveryDedupService.class);
        var recovery = mock(EmailRecoveryService.class);
        var consumer = new EmailConsumer(sender, dedup, new RabbitMQConfig().rabbitMessageConverter(), recovery);
        var method = EmailConsumer.class.getMethod("consume", Message.class, Channel.class, long.class);
        var adapter = new MessagingMessageListenerAdapter(consumer, method);
        adapter.setMessageConverter(new NotificationRabbitConfig().rawEmailMessageConverter());
        var factory = new DefaultMessageHandlerMethodFactory();
        factory.afterPropertiesSet();
        adapter.setHandlerAdapter(new HandlerAdapter(factory.createInvocableHandlerMethod(consumer, method)));
        var props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setHeader("__TypeId__", "dev.booking.sports.notification.email.EmailQueueMessage");
        props.setDeliveryTag(42L);
        var raw = new Message("{invalid json".getBytes(), props);
        var channel = mock(Channel.class);
        adapter.onMessage(raw, channel);
        verifyNoInteractions(sender, dedup);
        var order = inOrder(recovery, channel);
        order.verify(recovery).recover(eq(raw), any(RuntimeException.class));
        order.verify(channel).basicAck(42L, false);
    }
}
