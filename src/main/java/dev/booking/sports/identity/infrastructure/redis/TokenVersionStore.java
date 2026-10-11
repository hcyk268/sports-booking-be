package dev.booking.sports.identity.infrastructure.redis;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;


@Component
@RequiredArgsConstructor
public class TokenVersionStore {

	private final StringRedisTemplate redis;

	public long currentOrCreate(UUID userId) {
		String key = AuthRedisKeys.tokenVersion(userId);
		String current = redis.opsForValue().get(key);
		if (current != null) {
			return Long.parseLong(current);
		}

		long initialVersion = Instant.now().toEpochMilli();
		if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, Long.toString(initialVersion)))) {
			return initialVersion;
		}
		String racedValue = redis.opsForValue().get(key);
		return racedValue == null ? currentOrCreate(userId) : Long.parseLong(racedValue);
	}

	public long increment(UUID userId) {
		String key = AuthRedisKeys.tokenVersion(userId);
		redis.opsForValue().setIfAbsent(key, Long.toString(Instant.now().toEpochMilli()));
		Long value = redis.opsForValue().increment(key);
		return value == null ? 0L : value;
	}
}
