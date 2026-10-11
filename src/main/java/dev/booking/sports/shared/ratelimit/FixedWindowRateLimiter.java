package dev.booking.sports.shared.ratelimit;

import java.time.Instant;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class FixedWindowRateLimiter {

	private static final String KEY_PREFIX = "rate-limit:";

	private static final DefaultRedisScript<Long> INCREMENT = new DefaultRedisScript<>("""
			local count = redis.call('INCR', KEYS[1])
			if count == 1 then
				redis.call('EXPIRE', KEYS[1], ARGV[1])
			end
			return count
			""", Long.class);

	private final StringRedisTemplate redis;

	public boolean tryConsume(String action, String subject, int maxRequests, int timeWindow) {
		long now = Instant.now().getEpochSecond();
		long windowStart = Math.floorDiv(now, timeWindow) * timeWindow;
		long ttl = windowStart + timeWindow - now;
		String key = KEY_PREFIX + action + ":" + subject + ":" + windowStart;

		Long count = redis.execute(INCREMENT, List.of(key), Long.toString(ttl));
		return count != null && count <= maxRequests;
	}
}
