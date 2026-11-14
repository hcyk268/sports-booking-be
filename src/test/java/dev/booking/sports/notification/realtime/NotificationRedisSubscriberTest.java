package dev.booking.sports.notification.realtime;

import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;
import dev.booking.sports.notification.config.NotificationRealtimeProperties;
import tools.jackson.databind.ObjectMapper;

class NotificationRedisSubscriberTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final NotificationSseRegistry registry = mock(NotificationSseRegistry.class);
    private final NotificationRealtimeProperties properties = new NotificationRealtimeProperties("test.notifications", 1, 4, 1, 4);
    private final NotificationRedisSubscriber subscriber = new NotificationRedisSubscriber(mapper, registry, properties);
    private byte[] channel() { return properties.channel().getBytes(StandardCharsets.UTF_8); }
    private Message message(byte[] body, byte[] channel) {
        var message = mock(Message.class);
        when(message.getBody()).thenReturn(body);
        when(message.getChannel()).thenReturn(channel);
        return message;
    }

    @Test void routesValidMessageToLocalRegistry() {
        var event = new NotificationChangedEvent(UUID.randomUUID(), UUID.randomUUID(), NotificationChangedEvent.Action.READ, UUID.randomUUID());
        subscriber.onMessage(message(mapper.writeValueAsBytes(event), channel()), null);
        verify(registry).publish(event);
    }

    @Test void malformedOversizedAndWrongChannelMessagesDoNotReachRegistry() {
        subscriber.onMessage(message("{invalid".getBytes(StandardCharsets.UTF_8), channel()), null);
        subscriber.onMessage(message(new byte[4097], channel()), null);
        subscriber.onMessage(message("null".getBytes(StandardCharsets.UTF_8), channel()), null);
        subscriber.onMessage(message("{}".getBytes(StandardCharsets.UTF_8), "other".getBytes(StandardCharsets.UTF_8)), null);
        verifyNoInteractions(registry);
    }

    @Test void confirmedResubscriptionRequestsRestSyncForOpenStreams() {
        subscriber.onChannelSubscribed(channel(), 1);
        subscriber.onChannelSubscribed(channel(), 1);
        verify(registry, times(2)).requestSync();
    }
}
