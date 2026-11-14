package dev.booking.sports.notification.realtime;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import dev.booking.sports.identity.publicapi.*;
import dev.booking.sports.notification.BookingNotificationFixtures;
import dev.booking.sports.notification.application.*;
import dev.booking.sports.notification.config.*;
import dev.booking.sports.notification.domain.model.Notification;
import dev.booking.sports.notification.domain.repository.NotificationRepository;
import dev.booking.sports.support.*;
import tools.jackson.databind.ObjectMapper;

@IntegrationTest
class NotificationRealtimeIntegrationTest extends BaseIntegrationTest {
    @Autowired BookingNotificationHandler bookingHandler;
    @Autowired NotificationService notifications;
    @Autowired NotificationRepository repository;
    @Autowired PlatformTransactionManager transactions;
    @Autowired RedisConnectionFactory redis;
    @Autowired ObjectMapper mapper;
    @Autowired NotificationRealtimeProperties realtimeProperties;
    @Autowired NotificationSseProperties sseProperties;
    @Autowired ApplicationEventPublisher events;
    @Autowired JdbcTemplate jdbc;
    private UUID user;
    private Node first;
    private Node second;
    private Node outsider;

    @BeforeEach void setup() {
        user = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, password_hash, full_name, status) VALUES (?, ?, ?, ?, ?)",
                user, user + "@example.com", "test-hash", "Test User", "ACTIVE");
        first = new Node(user); second = new Node(user); outsider = new Node(UUID.randomUUID());
    }

    @AfterEach void cleanup() throws Exception {
        if (first != null) first.close();
        if (second != null) second.close();
        if (outsider != null) outsider.close();
        jdbc.update("DELETE FROM outbox_events WHERE event_type = 'notification.booking-email-requested' AND aggregate_id IN (SELECT id::text FROM notifications WHERE recipient_id = ?)", user);
        jdbc.update("DELETE FROM notifications WHERE recipient_id = ?", user);
        jdbc.update("DELETE FROM users WHERE id = ?", user);
    }

    @Test void createdEventArrivesOnTwoIndependentSubscribersOnlyAfterCommitAndIsDeduplicated() {
        var event = BookingNotificationFixtures.message(user, "booking.confirmed");
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            bookingHandler.handle(event);
            assertThat(first.payloads()).isEmpty();
            assertThat(second.payloads()).isEmpty();
        });
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(first.payloads()).hasSize(1);
            assertThat(second.payloads()).hasSize(1);
        });
        var payload = first.payloads().getFirst();
        assertThat(payload.action()).isEqualTo(NotificationChangedEvent.Action.CREATED);
        assertThat(repository.findByIdAndRecipientId(payload.notificationId(), user)).isPresent();
        assertThat(second.payloads()).containsExactly(payload);
        assertThat(outsider.payloads()).isEmpty();
        bookingHandler.handle(event);
        await().during(Duration.ofMillis(250)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            assertThat(first.payloads()).hasSize(1);
            assertThat(second.payloads()).hasSize(1);
        });
    }

    @Test void rollbackAndPublicationOutsideTransactionNeverPublish() {
        var event = BookingNotificationFixtures.message(user, "booking.confirmed");
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            bookingHandler.handle(event);
            throw new IllegalStateException("Rollback");
        })).isInstanceOf(IllegalStateException.class);
        events.publishEvent(new NotificationChangedEvent(UUID.randomUUID(), user, NotificationChangedEvent.Action.READ_ALL, null));
        await().during(Duration.ofMillis(250)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
            assertThat(first.payloads()).isEmpty();
            assertThat(second.payloads()).isEmpty();
        });
        assertThat(repository.countByRecipientIdAndReadAtIsNull(user)).isZero();
    }

    @Test void readAndReadAllPublishOnlyWhenRowsActuallyChange() {
        UUID one = saveNotification();
        UUID two = saveNotification();
        notifications.markRead(user, one);
        notifications.markRead(user, one);
        notifications.markAllRead(user);
        notifications.markAllRead(user);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(first.payloads()).hasSize(2);
            assertThat(second.payloads()).hasSize(2);
        });
        assertThat(first.payloads()).extracting(NotificationChangedEvent.Payload::action)
                .containsExactlyInAnyOrder(NotificationChangedEvent.Action.READ, NotificationChangedEvent.Action.READ_ALL);
        assertThat(first.payloads()).filteredOn(value -> value.action() == NotificationChangedEvent.Action.READ)
                .extracting(NotificationChangedEvent.Payload::notificationId).containsExactly(one);
        assertThat(repository.findById(two).orElseThrow().getReadAt()).isNotNull();
        await().during(Duration.ofMillis(250)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(first.payloads()).hasSize(2));
        assertThat(outsider.payloads()).isEmpty();
    }

    @Test void resubscriptionSignalsRestSyncWithoutReplayingMissedMessages() {
        UUID id = saveNotification();
        second.listener.stop();
        notifications.markRead(user, id);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(first.payloads()).hasSize(1));
        assertThat(second.payloads()).isEmpty();
        second.emitter.values.clear();
        second.listener.start();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(second.emitter.values.toString()).contains("event:notification.sync", "SUBSCRIPTION_READY"));
        assertThat(second.payloads()).isEmpty();
        assertThat(repository.countByRecipientIdAndReadAtIsNull(user)).isZero();
    }

    private UUID saveNotification() {
        return repository.saveAndFlush(Notification.create(user, "BOOKING_CONFIRMED", "Title", "Body",
                "BOOKING", UUID.randomUUID(), UUID.randomUUID())).getId();
    }

    private final class Node implements AutoCloseable {
        final CapturingEmitter emitter = new CapturingEmitter();
        final ExecutorService subscriberExecutor = Executors.newSingleThreadExecutor();
        final RedisMessageListenerContainer listener = new RedisMessageListenerContainer();
        final NotificationSseRegistry registry;
        Node(UUID recipient) {
            var verifier = mock(StreamSessionVerifier.class);
            when(verifier.isActive(any())).thenReturn(true);
            registry = new NotificationSseRegistry(verifier, sseProperties, Clock.systemUTC(),
                    Executors.newFixedThreadPool(2), ignored -> emitter);
            registry.open(new StreamSession(recipient, "test-jti", 1, Instant.now().plusSeconds(300)));
            listener.setConnectionFactory(redis);
            listener.setTaskExecutor(subscriberExecutor);
            listener.addMessageListener(new NotificationRedisSubscriber(mapper, registry, realtimeProperties),
                    new ChannelTopic(realtimeProperties.channel()));
            listener.afterPropertiesSet();
            listener.start();
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(emitter.values.toString()).contains("event:notification.sync"));
        }
        List<NotificationChangedEvent.Payload> payloads() {
            return emitter.values.stream().filter(NotificationChangedEvent.Payload.class::isInstance)
                    .map(NotificationChangedEvent.Payload.class::cast).toList();
        }
        @Override public void close() throws Exception {
            listener.stop();
            listener.destroy();
            registry.shutdown();
            subscriberExecutor.shutdownNow();
        }
    }

    private static final class CapturingEmitter extends SseEmitter {
        final List<Object> values = new CopyOnWriteArrayList<>();
        @Override public void send(SseEventBuilder event) throws IOException {
            event.build().forEach(value -> values.add(value.getData()));
        }
    }
}
