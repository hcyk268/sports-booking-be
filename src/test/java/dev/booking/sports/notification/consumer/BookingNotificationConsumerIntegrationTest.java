package dev.booking.sports.notification.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import dev.booking.sports.booking.infrastructure.outbox.BookingOutboxPublicationHandler;
import dev.booking.sports.notification.BookingNotificationFixtures;
import dev.booking.sports.notification.application.BookingNotificationHandler;
import dev.booking.sports.notification.config.BookingNotificationRabbitConfig;
import dev.booking.sports.shared.messaging.ConfirmedRabbitPublisher;
import dev.booking.sports.shared.outbox.OutboxEvent;
import dev.booking.sports.support.BaseIntegrationTest;
import dev.booking.sports.support.IntegrationTest;
import tools.jackson.databind.ObjectMapper;

@IntegrationTest
class BookingNotificationConsumerIntegrationTest extends BaseIntegrationTest {
    @Autowired RabbitListenerEndpointRegistry listeners;
    @Autowired RabbitTemplate rabbit;
    @Autowired ConfirmedRabbitPublisher publisher;
    @Autowired BookingOutboxPublicationHandler bookingPublisher;
    @Autowired BookingNotificationHandler handler;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired dev.booking.sports.shared.outbox.OutboxRepository outboxes;
    @Autowired dev.booking.sports.notification.outbox.BookingEmailOutboxPublicationHandler emailPublisher;
    @Autowired dev.booking.sports.notification.delivery.EmailDeliveryDedupService emailDedup;
    @Autowired org.springframework.amqp.support.converter.JacksonJsonMessageConverter emailConverter;
    @Autowired dev.booking.sports.notification.service.EmailRecoveryService emailRecovery;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    private final List<UUID> recipients = new ArrayList<>();

    @BeforeEach void setup() {
        listeners.stop();
        purgeQueues();
    }

    @AfterEach void cleanup() {
        listeners.getListenerContainer("bookingNotificationConsumer").stop();
        purgeQueues();
        for (UUID recipient : recipients) {
            jdbc.update("DELETE FROM processed_email_deliveries WHERE outbox_event_id IN (SELECT event_id FROM outbox_events WHERE aggregate_id IN (SELECT id::text FROM notifications WHERE recipient_id = ?))", recipient);
            jdbc.update("DELETE FROM outbox_events WHERE event_type = 'notification.booking-email-requested' AND aggregate_id IN (SELECT id::text FROM notifications WHERE recipient_id = ?)", recipient);
            jdbc.update("DELETE FROM notifications WHERE recipient_id = ?", recipient);
            jdbc.update("DELETE FROM users WHERE id = ?", recipient);
        }
    }

    @ParameterizedTest
    @CsvSource({ "booking.confirmed,BOOKING_CONFIRMED", "booking.cancelled,BOOKING_CANCELLED", "booking.expired,BOOKING_EXPIRED" })
    void producerEnvelopeIsRoutedAndStoredOnceOnRepeatedPublication(String eventType, String type) throws Exception {
        UUID recipient = recipient(UUID.randomUUID());
        var event = BookingNotificationFixtures.message(recipient, eventType);
        var outbox = new OutboxEvent("BOOKING", event.aggregateId(), eventType, 1,
                mapper.writeValueAsString(event.payload()));
        listeners.getListenerContainer("bookingNotificationConsumer").start();
        bookingPublisher.publish(outbox);
        bookingPublisher.publish(outbox);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(count(recipient)).isEqualTo(1));
        assertThat(jdbc.queryForMap("SELECT type, resource_id, source_event_id, read_at FROM notifications WHERE recipient_id = ?", recipient))
                .containsEntry("type", type)
                .containsEntry("resource_id", UUID.fromString(event.aggregateId()))
                .containsEntry("source_event_id", outbox.getEventId())
                .containsEntry("read_at", null);
        listeners.getListenerContainer("bookingNotificationConsumer").stop();
        assertThat(emailIntents(recipient)).isEqualTo(1);
        UUID notificationId = jdbc.queryForObject("SELECT id FROM notifications WHERE recipient_id = ?", UUID.class, recipient);
        var emailEvent = outboxes.findAll().stream()
                .filter(value -> value.getAggregateId().equals(notificationId.toString())).findFirst().orElseThrow();
        emailPublisher.publish(emailEvent);
        var rawEmail = rabbit.receive(dev.booking.sports.notification.config.NotificationRabbitConfig.EMAIL_QUEUE, 5_000);
        assertThat(rawEmail).isNotNull();
        var email = (dev.booking.sports.notification.email.EmailQueueMessage) emailConverter.fromMessage(rawEmail);
        assertThat(email.email().to()).isEqualTo(recipient + "@example.com");
        assertThat(email.email().subject()).contains("BK-TEST-001");
        assertThat(email.email().htmlBody()).contains("300.000 VND", "BK-TEST-001");
        var sender = mock(dev.booking.sports.notification.email.EmailSender.class);
        var emailConsumer = new EmailConsumer(sender, emailDedup, emailConverter, emailRecovery);
        var channel = mock(com.rabbitmq.client.Channel.class);
        emailConsumer.consume(rawEmail, channel, 42L);
        emailConsumer.consume(rawEmail, channel, 43L);
        verify(sender, times(1)).send(email.email());
    }

    @Test void concurrentHandlerTransactionsDeduplicateAndDoNotResetReadState() throws Exception {
        UUID recipient = recipient(UUID.randomUUID());
        var event = BookingNotificationFixtures.message(recipient, "booking.confirmed");
        try (var executor = Executors.newFixedThreadPool(4)) {
            var futures = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 8; i++) futures.add(executor.submit(() -> handler.handle(event)));
            for (var future : futures) future.get(10, TimeUnit.SECONDS);
        }
        assertThat(count(recipient)).isEqualTo(1);
        assertThat(emailIntents(recipient)).isEqualTo(1);
        jdbc.update("UPDATE notifications SET read_at = TIMESTAMPTZ '2026-11-12 00:00:00Z' WHERE recipient_id = ?", recipient);
        handler.handle(event);
        assertThat(count(recipient)).isEqualTo(1);
        assertThat(emailIntents(recipient)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT read_at IS NOT NULL FROM notifications WHERE recipient_id = ?", Boolean.class, recipient)).isTrue();
    }

    @Test void notificationAndEmailIntentRollBackTogetherAndCanBeRetried() {
        UUID recipient = recipient(UUID.randomUUID());
        var event = BookingNotificationFixtures.message(recipient, "booking.confirmed");
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        long previousIntents = outboxes.countByEventType("notification.booking-email-requested");
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            handler.handle(event);
            throw new IllegalStateException("Simulated failure before commit");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(count(recipient)).isZero();
        assertThat(emailIntents(recipient)).isZero();
        assertThat(outboxes.countByEventType("notification.booking-email-requested")).isEqualTo(previousIntents);
        handler.handle(event);
        assertThat(count(recipient)).isEqualTo(1);
        assertThat(emailIntents(recipient)).isEqualTo(1);
        assertThat(outboxes.countByEventType("notification.booking-email-requested")).isEqualTo(previousIntents + 1);
    }

    private int emailIntents(UUID recipient) {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE event_type = 'notification.booking-email-requested' AND aggregate_id IN (SELECT id::text FROM notifications WHERE recipient_id = ?)", Integer.class, recipient);
    }

    @ParameterizedTest @ValueSource(booleans = { false, true })
    void malformedJsonAndUnsupportedVersionReachDedicatedDlq(boolean invalidVersion) {
        UUID recipient = recipient(UUID.randomUUID());
        var event = BookingNotificationFixtures.message(recipient, "booking.confirmed");
        byte[] body = invalidVersion
                ? mapper.writeValueAsBytes(new BookingNotificationMessage(event.eventId(), event.aggregateType(),
                        event.aggregateId(), event.eventType(), 2, event.payload()))
                : "{invalid json".getBytes(StandardCharsets.UTF_8);
        var raw = new Message(body);
        raw.getMessageProperties().setHeader("__TypeId__", "untrusted.ProducerClass");
        listeners.getListenerContainer("bookingNotificationConsumer").start();
        publisher.publish("", BookingNotificationRabbitConfig.QUEUE, raw);

        var deadLetter = rabbit.receive(BookingNotificationRabbitConfig.DLQ, 15_000);
        assertThat(deadLetter).isNotNull();
        assertThat(deadLetter.getBody()).containsExactly(body);
        assertThat(count(recipient)).isZero();
    }

    @Test void failedInsertRollsBackAndRetryTtlReturnsMessageForSuccessfulCommit() {
        UUID recipient = UUID.randomUUID();
        var event = BookingNotificationFixtures.message(recipient, "booking.confirmed");
        listeners.getListenerContainer("bookingNotificationConsumer").start();
        publisher.publish("", BookingNotificationRabbitConfig.QUEUE, new Message(mapper.writeValueAsBytes(event)));

        await().atMost(Duration.ofSeconds(7)).untilAsserted(() -> {
            var properties = new RabbitAdmin(rabbit).getQueueProperties(BookingNotificationRabbitConfig.retryQueue(1));
            assertThat(properties).isNotNull();
            assertThat(((Number) properties.get(RabbitAdmin.QUEUE_MESSAGE_COUNT)).longValue()).isEqualTo(1L);
        });
        assertThat(count(recipient)).isZero();
        recipient(recipient);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(count(recipient)).isEqualTo(1));
        assertThat(rabbit.receive(BookingNotificationRabbitConfig.DLQ, 100)).isNull();
    }

    @Test void heldEventsAreNotBoundToTheNotificationInbox() {
        publisher.publish(BookingNotificationRabbitConfig.SOURCE_EXCHANGE, "booking.held", new Message(new byte[0]));
        assertThat(rabbit.receive(BookingNotificationRabbitConfig.QUEUE, 250)).isNull();
    }

    private UUID recipient(UUID id) {
        jdbc.update("INSERT INTO users(id, email, password_hash, full_name, status) VALUES (?, ?, ?, ?, ?)",
                id, id + "@example.com", "unused-test-hash", "Notification Test", "ACTIVE");
        recipients.add(id);
        return id;
    }

    private int count(UUID recipient) {
        return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE recipient_id = ?", Integer.class, recipient);
    }

    private void purgeQueues() {
        var admin = new RabbitAdmin(rabbit);
        admin.purgeQueue(BookingNotificationRabbitConfig.QUEUE);
        admin.purgeQueue(BookingNotificationRabbitConfig.DLQ);
        for (int i = 1; i <= BookingNotificationRabbitConfig.MAX_RETRIES; i++) {
            admin.purgeQueue(BookingNotificationRabbitConfig.retryQueue(i));
        }
    }
}
