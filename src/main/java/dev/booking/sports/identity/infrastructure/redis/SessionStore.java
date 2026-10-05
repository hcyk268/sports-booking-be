package dev.booking.sports.identity.infrastructure.redis;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;


@Component
@RequiredArgsConstructor
public class SessionStore {

	private static final String FIELD_REFRESH_HASH = "refreshHash";
	private static final String FIELD_USER_AGENT = "userAgent";
	private static final String FIELD_IP = "ip";
	private static final String FIELD_ISSUED_AT = "issuedAt";
	private static final String FIELD_LAST_USED_AT = "lastUsedAt";
	private static final DefaultRedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>("""
			if redis.call('EXISTS', KEYS[1]) == 0 then
			    return 0
			end
			if redis.call('HGET', KEYS[1], 'refreshHash') ~= ARGV[1] then
			    return -1
			end
			redis.call('HSET', KEYS[1], 'refreshHash', ARGV[2], 'lastUsedAt', ARGV[3])
			redis.call('PEXPIRE', KEYS[1], ARGV[4])
			redis.call('SADD', KEYS[2], ARGV[5])
			redis.call('PEXPIRE', KEYS[2], ARGV[4])
			return 1
			""", Long.class);

	private final StringRedisTemplate redis;

	public record Session(String refreshHash, String userAgent, String ip, Instant issuedAt, Instant lastUsedAt) {
	}

	public enum RotationResult {
		ROTATED,
		SESSION_NOT_FOUND,
		HASH_MISMATCH
	}

	public void create(UUID userId, String jti, String refreshHash, String userAgent, String ip, Duration ttl) {
		String now = Instant.now().toString();
		String sessionKey = AuthRedisKeys.session(userId, jti);

		redis.opsForHash().putAll(sessionKey, Map.of(
				FIELD_REFRESH_HASH, refreshHash,
				FIELD_USER_AGENT, userAgent == null ? "" : userAgent,
				FIELD_IP, ip == null ? "" : ip,
				FIELD_ISSUED_AT, now,
				FIELD_LAST_USED_AT, now));
		redis.expire(sessionKey, ttl);

		String indexKey = AuthRedisKeys.sessionIndex(userId);
		redis.opsForSet().add(indexKey, jti);
		redis.expire(indexKey, ttl);
	}

	public Optional<Session> find(UUID userId, String jti) {
		Map<Object, Object> entries = redis.opsForHash().entries(AuthRedisKeys.session(userId, jti));
		if (entries.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new Session(
				(String) entries.get(FIELD_REFRESH_HASH),
				(String) entries.get(FIELD_USER_AGENT),
				(String) entries.get(FIELD_IP),
				Instant.parse((String) entries.get(FIELD_ISSUED_AT)),
				Instant.parse((String) entries.get(FIELD_LAST_USED_AT))));
	}

	public RotationResult rotateRefreshHash(
			UUID userId,
			String jti,
			String expectedRefreshHash,
			String newRefreshHash,
			Duration ttl) {
		String sessionKey = AuthRedisKeys.session(userId, jti);
		String indexKey = AuthRedisKeys.sessionIndex(userId);
		Long result = redis.execute(
				ROTATE_SCRIPT,
				List.of(sessionKey, indexKey),
				expectedRefreshHash,
				newRefreshHash,
				Instant.now().toString(),
				Long.toString(ttl.toMillis()),
				jti);
		if (Long.valueOf(1L).equals(result)) {
			return RotationResult.ROTATED;
		}
		if (Long.valueOf(-1L).equals(result)) {
			return RotationResult.HASH_MISMATCH;
		}
		return RotationResult.SESSION_NOT_FOUND;
	}

	public void delete(UUID userId, String jti) {
		redis.delete(AuthRedisKeys.session(userId, jti));
		redis.opsForSet().remove(AuthRedisKeys.sessionIndex(userId), jti);
	}

	public void deleteAll(UUID userId) {
		String indexKey = AuthRedisKeys.sessionIndex(userId);
		Set<String> jtis = redis.opsForSet().members(indexKey);

		if (jtis != null && !jtis.isEmpty()) {
			redis.delete(jtis.stream().map(jti -> AuthRedisKeys.session(userId, jti)).toList());
		}
		redis.delete(indexKey);
	}
}
