package dev.booking.sports.identity.infrastructure.redis;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;


@Component
@RequiredArgsConstructor
public class AuthStateStore {

	private final StringRedisTemplate redis;

	public record AuthState(long tokenVersion, boolean tokenVersionExists, boolean accessTokenBlacklisted) {
	}

	public AuthState read(UUID userId, String jti) {
		byte[] tokenVersionKey = AuthRedisKeys.tokenVersion(userId).getBytes(StandardCharsets.UTF_8);
		byte[] blacklistKey = AuthRedisKeys.accessTokenBlacklist(userId, jti)
				.getBytes(StandardCharsets.UTF_8);

		List<Object> results = redis.executePipelined((RedisCallback<Object>) connection -> {
			connection.stringCommands().get(tokenVersionKey);
			connection.keyCommands().exists(blacklistKey);
			return null;
		}, RedisSerializer.string());

		Object tokenVersion = results.get(0);
		return new AuthState(
				parseTokenVersion(tokenVersion),
				tokenVersion != null,
				Boolean.TRUE.equals(results.get(1)));
	}

	private long parseTokenVersion(Object result) {
		return result == null ? 0L : Long.parseLong((String) result);
	}
}
