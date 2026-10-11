package dev.booking.sports.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.sendgrid")
public record SendGridProperties(String apiKey, String fromEmail, String fromName) {
}
