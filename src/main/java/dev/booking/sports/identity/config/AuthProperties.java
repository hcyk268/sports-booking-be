package dev.booking.sports.identity.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(Duration verificationTokenTtl, Otp otp) {
	public record Otp(int length, Duration ttl, int maxAttempts) {
	}
}
