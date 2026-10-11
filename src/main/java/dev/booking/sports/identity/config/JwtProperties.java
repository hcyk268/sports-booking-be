package dev.booking.sports.identity.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secretKey, String refreshSecretKey, long expirationMs, long refreshExpirationMs) {

	public Duration accessTokenTtl() {
		return Duration.ofMillis(expirationMs);
	}

	public Duration refreshTokenTtl() {
		return Duration.ofMillis(refreshExpirationMs);
	}
}
