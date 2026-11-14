package dev.booking.sports.notification.realtime;

import java.nio.charset.StandardCharsets;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.SubscriptionListener;
import org.springframework.stereotype.Component;
import dev.booking.sports.notification.config.NotificationRealtimeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationRedisSubscriber implements MessageListener, SubscriptionListener {

    private final ObjectMapper mapper;
    private final NotificationSseRegistry registry;
    private final NotificationRealtimeProperties properties;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        if (!properties.channel().equals(new String(message.getChannel(), StandardCharsets.UTF_8))) return;
        try {
            if (message.getBody().length > 4096) throw new IllegalArgumentException("Realtime envelope is too large");
            var event = mapper.readValue(message.getBody(), NotificationChangedEvent.class);
            if (event == null) throw new IllegalArgumentException("Realtime envelope is required");
            registry.publish(event);
        } catch (RuntimeException failure) {
            log.warn("Ignored invalid notification realtime message", failure);
        }
    }

    @Override
    public void onChannelSubscribed(byte[] channel, long count) {
        if (properties.channel().equals(new String(channel, StandardCharsets.UTF_8))) registry.requestSync();
    }
}
