package dev.booking.sports.notification.realtime;

import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import dev.booking.sports.notification.config.NotificationRealtimeProperties;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
public class NotificationRedisPublisher {

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final NotificationRealtimeProperties properties;
    private final Executor executor;

    public NotificationRedisPublisher(StringRedisTemplate redis, ObjectMapper mapper,
            NotificationRealtimeProperties properties,
            @Qualifier("notificationRealtimePublisherExecutor") Executor executor) {
        this.redis = redis;
        this.mapper = mapper;
        this.properties = properties;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommitted(NotificationChangedEvent event) {
        try {
            executor.execute(() -> {
                try {
                    redis.convertAndSend(properties.channel(), mapper.writeValueAsString(event));
                } catch (RuntimeException failure) {
                    log.warn("Realtime publish failed for event {}; committed notification is retained", event.eventId(), failure);
                }
            });
        } catch (RuntimeException rejected) {
            log.warn("Realtime publish task rejected for event {}; clients must resync through REST", event.eventId(), rejected);
        }
    }
}
