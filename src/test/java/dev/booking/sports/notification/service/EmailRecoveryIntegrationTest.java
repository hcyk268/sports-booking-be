package dev.booking.sports.notification.service;

import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.core.MessageProperties;
import dev.booking.sports.notification.config.NotificationRabbitConfig;
import dev.booking.sports.notification.email.*;
import dev.booking.sports.support.BaseIntegrationTest;
import dev.booking.sports.support.IntegrationTest;

@IntegrationTest
class EmailRecoveryIntegrationTest extends BaseIntegrationTest {
    @Autowired EmailRecoveryService recovery;
    @Autowired RabbitTemplate rabbit;
    @Autowired JacksonJsonMessageConverter converter;
    @Autowired RabbitListenerEndpointRegistry listeners;

    @BeforeEach void clearQueues() {
        listeners.stop();
        var admin = new RabbitAdmin(rabbit);
        admin.purgeQueue(NotificationRabbitConfig.EMAIL_QUEUE);
        admin.purgeQueue(NotificationRabbitConfig.EMAIL_DLQ);
        for (int i = 1; i <= 3; i++) admin.purgeQueue(NotificationRabbitConfig.retryQueue(i));
    }

    private org.springframework.amqp.core.Message message() {
        return converter.toMessage(new EmailQueueMessage(UUID.randomUUID(),
                new EmailMessage("test@example.com", "Test", "Subject", "body")), new MessageProperties());
    }

    @Test void transientFailureReturnsThroughQuorumTtlQueue() {
        var original = message();
        recovery.recover(original, new EmailDeliveryException("429"));
        assertThat(rabbit.receive(NotificationRabbitConfig.EMAIL_QUEUE, 100)).isNull();
        var retried = rabbit.receive(NotificationRabbitConfig.EMAIL_QUEUE, 15_000);
        assertThat(retried).isNotNull();
        assertThat(retried.getBody()).containsExactly(original.getBody());
        assertThat(retried.getMessageProperties().getHeaders()).containsEntry(NotificationRabbitConfig.RETRY_HEADER, 1);
    }

    @Test void permanentFailureCanBeReplayedWithSameIdentityAndFreshRetryBudget() {
        var original = message();
        original.getMessageProperties().setHeader(NotificationRabbitConfig.RETRY_HEADER, 3);
        recovery.recover(original, new EmailDeliveryException("invalid recipient", false));
        assertThat(recovery.replayDeadLetters(25)).isEqualTo(1);
        var replayed = rabbit.receive(NotificationRabbitConfig.EMAIL_QUEUE, 5_000);
        assertThat(replayed).isNotNull();
        assertThat(replayed.getBody()).containsExactly(original.getBody());
        assertThat(replayed.getMessageProperties().getHeaders()).containsEntry(NotificationRabbitConfig.RETRY_HEADER, 0);
        assertThat(rabbit.receive(NotificationRabbitConfig.EMAIL_DLQ, 100)).isNull();
    }
}
