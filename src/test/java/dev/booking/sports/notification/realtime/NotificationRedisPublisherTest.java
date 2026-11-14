package dev.booking.sports.notification.realtime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import dev.booking.sports.notification.config.NotificationRealtimeProperties;
import tools.jackson.databind.ObjectMapper;

class NotificationRedisPublisherTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final NotificationRealtimeProperties properties = new NotificationRealtimeProperties("test.notifications", 1, 4, 1, 4);
    private final NotificationChangedEvent event = new NotificationChangedEvent(UUID.randomUUID(), UUID.randomUUID(),
            NotificationChangedEvent.Action.READ_ALL, null);

    @Test void publishesCompleteInternalEnvelopeAndAcceptsZeroSubscribers() {
        when(redis.convertAndSend(anyString(), anyString())).thenReturn(0L);
        var publisher = new NotificationRedisPublisher(redis, mapper, properties, Runnable::run);
        publisher.onCommitted(event);
        verify(redis).convertAndSend(properties.channel(), mapper.writeValueAsString(event));
    }

    @Test void redisFailureCannotEscapeAfterCommit() {
        when(redis.convertAndSend(anyString(), anyString())).thenThrow(new IllegalStateException("Redis down"));
        var publisher = new NotificationRedisPublisher(redis, mapper, properties, Runnable::run);
        assertThatCode(() -> publisher.onCommitted(event)).doesNotThrowAnyException();
    }

    @Test void saturatedExecutorDoesNotRunRedisOnBusinessThreadOrThrow() {
        var publisher = new NotificationRedisPublisher(redis, mapper, properties, task -> { throw new RejectedExecutionException(); });
        assertThatCode(() -> publisher.onCommitted(event)).doesNotThrowAnyException();
        verifyNoInteractions(redis);
    }
}
