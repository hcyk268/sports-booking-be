package dev.booking.sports.notification.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NotificationSseProperties.class)
public class NotificationSseConfig {}
