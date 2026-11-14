package dev.booking.sports.notification.realtime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import dev.booking.sports.identity.publicapi.*;
import dev.booking.sports.notification.config.NotificationSseProperties;
import dev.booking.sports.shared.exception.ApiException;

class NotificationSseRegistryTest {
    private final StreamSessionVerifier verifier = mock(StreamSessionVerifier.class);
    private final TestClock clock = new TestClock();
    private final ManualExecutor executor = new ManualExecutor();
    private final List<FakeEmitter> emitters = new ArrayList<>();
    private NotificationSseRegistry registry;

    @BeforeEach void setup() {
        when(verifier.isActive(any())).thenReturn(true);
        registry = new NotificationSseRegistry(verifier, new NotificationSseProperties(
                Duration.ofSeconds(15), Duration.ofMinutes(5), Duration.ofSeconds(10), 2, 3, 2, 1, 2),
                clock, executor, timeout -> { var emitter = new FakeEmitter(timeout); emitters.add(emitter); return emitter; });
    }
    @AfterEach void cleanup() { registry.shutdown(); }
    private StreamSession session(UUID user) { return new StreamSession(user, "jti", 1, clock.instant().plusSeconds(900)); }
    private NotificationChangedEvent event(UUID user) {
        return new NotificationChangedEvent(UUID.randomUUID(), user, NotificationChangedEvent.Action.CREATED, UUID.randomUUID());
    }

    @Test void readyAndChangesReachAllOwnedConnectionsOnly() {
        UUID user = UUID.randomUUID();
        registry.open(session(user)); registry.open(session(user)); registry.open(session(UUID.randomUUID()));
        executor.runAll();
        assertThat(emitters).allSatisfy(emitter -> assertThat(emitter.values.toString()).contains("event:ready"));
        var event = event(user);
        registry.publish(event);
        executor.runAll();
        assertThat(emitters.get(0).values).contains(event.payload());
        assertThat(emitters.get(1).values).contains(event.payload());
        assertThat(emitters.get(2).values).doesNotContain(event.payload());
        assertThat(registry.connectionCount()).isEqualTo(3);
    }

    @Test void limitsAndTimeoutCleanupDoNotRemoveSiblingConnections() {
        UUID user = UUID.randomUUID();
        registry.open(session(user)); registry.open(session(user)); executor.runAll();
        assertThatThrownBy(() -> registry.open(session(user))).isInstanceOf(ApiException.class)
                .hasMessageContaining("limit");
        registry.open(session(UUID.randomUUID())); executor.runAll();
        assertThatThrownBy(() -> registry.open(session(UUID.randomUUID()))).isInstanceOf(ApiException.class);
        emitters.get(0).timeout.run();
        assertThat(registry.connectionCount()).isEqualTo(2);
        registry.open(session(user)); executor.runAll();
        assertThat(registry.connectionCount()).isEqualTo(3);
    }

    @Test void heartbeatRechecksRevocationAndClosesFailedSession() {
        registry.open(session(UUID.randomUUID())); executor.runAll();
        int original = emitters.getFirst().values.size();
        clock.advance(15); registry.maintain(); executor.runAll();
        assertThat(emitters.getFirst().values.size()).isGreaterThan(original);
        when(verifier.isActive(any())).thenReturn(false);
        clock.advance(15); registry.maintain(); executor.runAll();
        assertThat(registry.connectionCount()).isZero();
        assertThat(emitters.getFirst().completed).isTrue();
    }

    @Test void streamCannotOutliveTokenOrMaximumConnectionLifetime() {
        var user = UUID.randomUUID();
        registry.open(new StreamSession(user, "jti", 1, clock.instant().plusSeconds(2)));
        executor.runAll();
        assertThat(emitters.getFirst().getTimeout()).isEqualTo(2000L);
        clock.advance(2); registry.maintain();
        assertThat(registry.connectionCount()).isZero();
        registry.open(session(user)); executor.runAll();
        assertThat(emitters.getLast().getTimeout()).isEqualTo(300000L);
        clock.advance(300); registry.maintain();
        assertThat(registry.connectionCount()).isZero();
    }

    @Test void perConnectionOverflowClosesSlowConnection() {
        UUID user = UUID.randomUUID();
        registry.open(session(user)); // ready is waiting for executor
        registry.publish(event(user));
        registry.publish(event(user));
        assertThat(registry.connectionCount()).isZero();
        executor.runAll();
        assertThat(emitters.getFirst().values).isEmpty();
    }

    @Test void queuedSendTimeoutAndExecutorRejectionFreeSlots() {
        registry.open(session(UUID.randomUUID()));
        clock.advance(10); registry.maintain();
        assertThat(registry.connectionCount()).isZero();
        executor.reject = true;
        assertThatThrownBy(() -> registry.open(session(UUID.randomUUID()))).isInstanceOf(ApiException.class);
        assertThat(registry.connectionCount()).isZero();
    }

    @Test void sendFailureAndAuthStoreFailureCloseConnections() {
        registry.open(session(UUID.randomUUID()));
        emitters.getFirst().fail = true;
        executor.runAll();
        assertThat(registry.connectionCount()).isZero();
        registry.open(session(UUID.randomUUID()));
        when(verifier.isActive(any())).thenThrow(new IllegalStateException("Redis unavailable"));
        executor.runAll();
        assertThat(registry.connectionCount()).isZero();
    }

    @Test void shutdownClosesConnectionsAndRejectsNewStreams() {
        registry.open(session(UUID.randomUUID())); executor.runAll();
        registry.shutdown();
        assertThat(registry.connectionCount()).isZero();
        assertThat(emitters.getFirst().completed).isTrue();
        assertThatThrownBy(() -> registry.open(session(UUID.randomUUID()))).isInstanceOf(ApiException.class);
    }

    @Test void contractRequiresNotificationIdExceptForReadAll() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> new NotificationChangedEvent(id, id, NotificationChangedEvent.Action.READ, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotificationChangedEvent(id, id, NotificationChangedEvent.Action.READ_ALL, id))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new NotificationChangedEvent(id, id, NotificationChangedEvent.Action.READ_ALL, null).payload().notificationId()).isNull();
    }

    @Test void watchdogInterruptsBlockedSendWithoutBlockingAnotherUser() throws Exception {
        var entered = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        var otherReady = new CountDownLatch(1);
        var created = new java.util.concurrent.atomic.AtomicInteger();
        var pool = Executors.newFixedThreadPool(2);
        var local = new NotificationSseRegistry(verifier, new NotificationSseProperties(
                Duration.ofSeconds(15), Duration.ofMinutes(5), Duration.ofSeconds(10), 2, 3, 2, 2, 2),
                clock, pool, timeout -> new SseEmitter(timeout) {
                    final int number = created.getAndIncrement();
                    @Override public void send(SseEventBuilder builder) throws IOException {
                        if (number != 0) { otherReady.countDown(); return; }
                        entered.countDown();
                        try { new CountDownLatch(1).await(); }
                        catch (InterruptedException failure) {
                            interrupted.countDown();
                            Thread.currentThread().interrupt();
                            throw new IOException("Interrupted blocked client", failure);
                        }
                    }
                });
        try {
            local.open(session(UUID.randomUUID()));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            local.open(session(UUID.randomUUID()));
            assertThat(otherReady.await(5, TimeUnit.SECONDS)).isTrue();
            // Wait for the second drain to clear its send timestamp before advancing the clock.
            pool.submit(() -> {}).get(5, TimeUnit.SECONDS);
            clock.advance(10);
            local.maintain();
            assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(local.connectionCount()).isEqualTo(1);
        } finally { local.shutdown(); }
    }

    private static class FakeEmitter extends SseEmitter {
        final List<Object> values = new ArrayList<>();
        Runnable timeout;
        Runnable completion;
        boolean completed;
        boolean fail;
        FakeEmitter(long timeout) { super(timeout); }
        @Override public void send(SseEventBuilder builder) throws IOException {
            if (fail) throw new IOException("Disconnected");
            builder.build().forEach(item -> values.add(item.getData()));
        }
        @Override public void onTimeout(Runnable callback) { timeout = callback; }
        @Override public void onCompletion(Runnable callback) { completion = callback; }
        @Override public void onError(Consumer<Throwable> callback) {}
        @Override public void complete() { completed = true; if (completion != null) completion.run(); }
    }
    private static class ManualExecutor extends AbstractExecutorService {
        final Queue<Runnable> tasks = new ArrayDeque<>();
        boolean reject;
        @Override public void execute(Runnable runnable) {
            if (reject) throw new RejectedExecutionException();
            tasks.add(runnable);
        }
        void runAll() { while (!tasks.isEmpty()) tasks.remove().run(); }
        @Override public void shutdown() { reject = true; }
        @Override public List<Runnable> shutdownNow() { reject = true; tasks.clear(); return List.of(); }
        @Override public boolean isShutdown() { return reject; }
        @Override public boolean isTerminated() { return reject; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
    }
    private static class TestClock extends Clock {
        volatile Instant now = Instant.parse("2026-11-14T00:00:00Z");
        void advance(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
