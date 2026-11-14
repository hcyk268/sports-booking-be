package dev.booking.sports.notification.realtime;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import dev.booking.sports.identity.publicapi.StreamSession;
import dev.booking.sports.identity.publicapi.StreamSessionVerifier;
import dev.booking.sports.notification.api.error.NotificationErrorCode;
import dev.booking.sports.notification.config.NotificationSseProperties;
import dev.booking.sports.shared.exception.ApiException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class NotificationSseRegistry {

    private final StreamSessionVerifier verifier;
    private final NotificationSseProperties properties;
    private final Clock clock;
    private final ExecutorService senders;
    private final LongFunction<SseEmitter> emitters;
    private final ScheduledExecutorService timer;
    private final Map<UUID, Map<UUID, Connection>> users = new HashMap<>();
    private int count;
    private boolean stopping;

    @Autowired
    public NotificationSseRegistry(StreamSessionVerifier verifier, NotificationSseProperties properties) {
        this(verifier, properties, Clock.systemUTC(), new ThreadPoolExecutor(
                properties.senderThreads(), properties.senderThreads(), 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.senderQueueCapacity()),
                Thread.ofPlatform().name("notification-sse-send-", 0).factory(), new ThreadPoolExecutor.AbortPolicy()),
                SseEmitter::new);
    }

    NotificationSseRegistry(StreamSessionVerifier verifier, NotificationSseProperties properties, Clock clock,
            ExecutorService senders, LongFunction<SseEmitter> emitters) {
        this.verifier = verifier;
        this.properties = properties;
        this.clock = clock;
        this.senders = senders;
        this.emitters = emitters;
        this.timer = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("notification-sse-watchdog").factory());
    }

    @PostConstruct void start() {
        timer.scheduleAtFixedRate(this::maintain, 1, 1, TimeUnit.SECONDS);
    }

    public SseEmitter open(StreamSession session) {
        Instant now = clock.instant();
        Instant deadline = now.plus(properties.connectionTimeout());
        if (session.expiresAt().isBefore(deadline)) deadline = session.expiresAt();
        if (!deadline.isAfter(now)) {
            throw new ApiException(NotificationErrorCode.NOTIFICATION_STREAM_UNAVAILABLE);
        }
        var connection = new Connection(session, deadline, emitters.apply(Math.max(1, deadline.toEpochMilli() - now.toEpochMilli())));
        synchronized (this) {
            if (stopping) throw new ApiException(NotificationErrorCode.NOTIFICATION_STREAM_UNAVAILABLE);
            var connections = users.get(session.userId());
            if (count >= properties.maxConnections()
                    || connections != null && connections.size() >= properties.maxConnectionsPerUser()) {
                throw new ApiException(NotificationErrorCode.NOTIFICATION_STREAM_LIMIT);
            }
            users.computeIfAbsent(session.userId(), ignored -> new HashMap<>()).put(connection.id, connection);
            count++;
        }
        connection.emitter.onCompletion(() -> close(connection));
        connection.emitter.onTimeout(() -> close(connection));
        connection.emitter.onError(error -> close(connection));
        enqueue(connection, new Frame("ready", new Ready(connection.id), null));
        if (connection.closed.get()) throw new ApiException(NotificationErrorCode.NOTIFICATION_STREAM_UNAVAILABLE);
        return connection.emitter;
    }

    public void publish(NotificationChangedEvent event) {
        List<Connection> connections;
        synchronized (this) {
            var owned = users.get(event.recipientId());
            connections = owned == null ? List.of() : List.copyOf(owned.values());
        }
        for (var connection : connections) {
            enqueue(connection, new Frame("notification.changed", event.payload(), event.eventId().toString()));
        }
    }

    public void requestSync() {
        List<Connection> connections;
        synchronized (this) { connections = users.values().stream().flatMap(map -> map.values().stream()).toList(); }
        for (var connection : connections) {
            enqueue(connection, new Frame("notification.sync", Map.of("reason", "SUBSCRIPTION_READY"), null));
        }
    }

    private void enqueue(Connection connection, Frame frame) {
        if (connection.closed.get()) return;
        if (!connection.frames.offer(frame)) { close(connection); return; }
        drain(connection);
    }

    private void drain(Connection connection) {
        if (!connection.draining.compareAndSet(false, true)) return;
        var task = new FutureTask<Void>(() -> {
            connection.writer = Thread.currentThread();
            try {
                Frame frame;
                while (!connection.closed.get() && (frame = connection.frames.poll()) != null) {
                    connection.sendingSince = clock.instant();
                    if (!clock.instant().isBefore(connection.deadline) || !verifier.isActive(connection.session)) {
                        close(connection);
                        return null;
                    }
                    if (connection.closed.get()) return null;
                    var event = SseEmitter.event();
                    if (frame.name == null) event.comment("heartbeat");
                    else event.name(frame.name).data(frame.data);
                    if (frame.id != null) event.id(frame.id);
                    connection.emitter.send(event);
                    connection.sendingSince = null;
                }
            } catch (IOException | RuntimeException failure) {
                close(connection);
            } finally {
                connection.writer = null;
                connection.sendingSince = null;
                connection.draining.set(false);
                if (!connection.closed.get() && !connection.frames.isEmpty()) drain(connection);
            }
            return null;
        });
        connection.task = task;
        connection.sendingSince = clock.instant(); // Includes time waiting in the bounded executor queue.
        try { senders.execute(task); }
        catch (RejectedExecutionException rejected) { close(connection); }
    }

    void maintain() {
        Instant now = clock.instant();
        List<Connection> connections;
        synchronized (this) { connections = users.values().stream().flatMap(map -> map.values().stream()).toList(); }
        for (var connection : connections) {
            try {
                Instant started = connection.sendingSince;
                if (!now.isBefore(connection.deadline)
                        || started != null && !now.isBefore(started.plus(properties.sendTimeout()))) {
                    close(connection);
                } else if (!now.isBefore(connection.lastHeartbeat.plus(properties.heartbeatInterval()))) {
                    connection.lastHeartbeat = now;
                    enqueue(connection, new Frame(null, null, null));
                }
            } catch (RuntimeException ignored) { close(connection); }
        }
    }

    private void close(Connection connection) {
        if (!connection.closed.compareAndSet(false, true)) return;
        synchronized (this) {
            var connections = users.get(connection.session.userId());
            if (connections != null && connections.remove(connection.id) != null) {
                count--;
                if (connections.isEmpty()) users.remove(connection.session.userId());
            }
        }
        connection.frames.clear();
        var task = connection.task;
        if (task != null && connection.writer != Thread.currentThread()) task.cancel(true);
        // Prevent cancelled, queued tasks from occupying the bounded executor indefinitely.
        if (senders instanceof ThreadPoolExecutor pool) pool.purge();
        try { connection.emitter.complete(); }
        catch (RuntimeException ignored) { /* The servlet may already have completed the response. */ }
    }

    synchronized int connectionCount() { return count; }

    @PreDestroy public void shutdown() {
        List<Connection> connections;
        synchronized (this) {
            stopping = true;
            connections = users.values().stream().flatMap(map -> map.values().stream()).toList();
        }
        timer.shutdownNow();
        connections.forEach(this::close);
        senders.shutdownNow();
    }

    private record Ready(UUID connectionId) {}
    private record Frame(String name, Object data, String id) {}

    private final class Connection {
        final UUID id = UUID.randomUUID();
        final StreamSession session;
        final Instant deadline;
        final SseEmitter emitter;
        final ArrayBlockingQueue<Frame> frames = new ArrayBlockingQueue<>(properties.connectionQueueCapacity());
        final AtomicBoolean closed = new AtomicBoolean();
        final AtomicBoolean draining = new AtomicBoolean();
        volatile Instant sendingSince;
        volatile Future<?> task;
        volatile Thread writer;
        Instant lastHeartbeat = clock.instant();

        Connection(StreamSession session, Instant deadline, SseEmitter emitter) {
            this.session = session;
            this.deadline = deadline;
            this.emitter = emitter;
        }
    }
}
