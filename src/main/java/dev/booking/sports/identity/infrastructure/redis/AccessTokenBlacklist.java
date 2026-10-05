package dev.booking.sports.identity.infrastructure.redis;

import java.time.Duration;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AccessTokenBlacklist {

	private static final String REVOKED = "1";

	private final StringRedisTemplate redis;

	public void add(UUID userId, String jti, Duration ttl) {
		if (ttl.isZero() || ttl.isNegative()) {
			return;
		}
		redis.opsForValue().set(AuthRedisKeys.accessTokenBlacklist(userId, jti), REVOKED, ttl);
	}
}
