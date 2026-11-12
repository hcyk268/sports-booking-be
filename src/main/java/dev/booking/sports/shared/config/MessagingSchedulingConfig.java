package dev.booking.sports.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class MessagingSchedulingConfig {
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        return scheduler("background-", 3);
    }

    @Bean
    public ThreadPoolTaskScheduler outboxScheduler() {
        return scheduler("outbox-", 1);
    }

    private ThreadPoolTaskScheduler scheduler(String prefix, int size) {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(size);
        scheduler.setThreadNamePrefix(prefix);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }
}
