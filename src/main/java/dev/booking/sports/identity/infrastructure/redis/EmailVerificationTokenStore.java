package dev.booking.sports.identity.infrastructure.redis;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class EmailVerificationTokenStore {

	private final StringRedisTemplate redis;

	public void issue(String tokenHash, UUID userId, Duration ttl) {
		redis.opsForValue().set(AuthRedisKeys.emailVerification(tokenHash), userId.toString(), ttl);
	}

	public Optional<UUID> consume(String tokenHash) {
		String userId = redis.opsForValue().getAndDelete(AuthRedisKeys.emailVerification(tokenHash));
		return Optional.ofNullable(userId).map(UUID::fromString);
	}
}
