package dev.booking.sports.identity.infrastructure.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OtpStore {

	private static final String FIELD_HASH = "hash";
	private static final String FIELD_ATTEMPTS = "attempts";

	private final StringRedisTemplate redis;

	public enum VerificationResult {
		OK,
		INVALID,
		NOT_FOUND,
		TOO_MANY_ATTEMPTS
	}

	public void issue(OtpPurpose purpose, UUID userId, String otpHash, Duration ttl) {
		String key = AuthRedisKeys.otp(purpose, userId);
		redis.delete(key);
		redis.opsForHash().putAll(key, Map.of(FIELD_HASH, otpHash, FIELD_ATTEMPTS, "0"));
		redis.expire(key, ttl);
	}

	public VerificationResult verify(OtpPurpose purpose, UUID userId, String otpHash, int maxAttempts) {
		String key = AuthRedisKeys.otp(purpose, userId);
		Object storedHash = redis.opsForHash().get(key, FIELD_HASH);

		if (storedHash == null) {
			return VerificationResult.NOT_FOUND;
		}

		if (matches((String) storedHash, otpHash)) {
			redis.delete(key);
			return VerificationResult.OK;
		}

		Long attempts = redis.opsForHash().increment(key, FIELD_ATTEMPTS, 1L);
		if (attempts != null && attempts >= maxAttempts) {
			redis.delete(key);
			return VerificationResult.TOO_MANY_ATTEMPTS;
		}
		return VerificationResult.INVALID;
	}

	public void discard(OtpPurpose purpose, UUID userId) {
		redis.delete(AuthRedisKeys.otp(purpose, userId));
	}

	private boolean matches(String storedHash, String candidateHash) {
		return MessageDigest.isEqual(
				storedHash.getBytes(StandardCharsets.UTF_8),
				candidateHash.getBytes(StandardCharsets.UTF_8));
	}
}
