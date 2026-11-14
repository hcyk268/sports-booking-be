package dev.booking.sports.notification.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import dev.booking.sports.notification.realtime.NotificationRedisSubscriber;

@Configuration
@EnableConfigurationProperties(NotificationRealtimeProperties.class)
public class NotificationRealtimeConfig {

    @Bean
    public ThreadPoolTaskExecutor notificationRealtimePublisherExecutor(NotificationRealtimeProperties properties) {
        return executor("notification-redis-publish-", properties.publisherThreads(), properties.publisherQueueCapacity(), false);
    }

    @Bean
    public ThreadPoolTaskExecutor notificationRealtimeSubscriberExecutor(NotificationRealtimeProperties properties) {
        return executor("notification-redis-subscribe-", properties.subscriberThreads(), properties.subscriberQueueCapacity(), true);
    }

    @Bean
    public RedisMessageListenerContainer notificationRedisListenerContainer(RedisConnectionFactory connectionFactory,
            NotificationRedisSubscriber subscriber, NotificationRealtimeProperties properties,
            @Qualifier("notificationRealtimeSubscriberExecutor") ThreadPoolTaskExecutor executor) {
        var container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setTaskExecutor(executor);
        container.setRecoveryInterval(5_000);
        container.addMessageListener(subscriber, new ChannelTopic(properties.channel()));
        return container;
    }

    private ThreadPoolTaskExecutor executor(String prefix, int threads, int queueCapacity, boolean callerRuns) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(prefix);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setRejectedExecutionHandler(callerRuns
                ? new ThreadPoolExecutor.CallerRunsPolicy() : new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
}
