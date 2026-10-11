package dev.booking.sports.identity.infrastructure.redis;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RolePermissionChangePendingStore {

	private final RedisTemplate<String, Object> redis;

	public void save(UUID actorUserId, String roleCode, Set<String> permissionCodes, Duration ttl) {
		String key = AuthRedisKeys.rolePermissionChangePending(actorUserId, roleCode);
		redis.opsForValue().set(key, normalize(permissionCodes), ttl);
	}

	public Optional<Set<String>> read(UUID actorUserId, String roleCode) {
		String key = AuthRedisKeys.rolePermissionChangePending(actorUserId, roleCode);
		Object payload = redis.opsForValue().get(key);
		if (payload == null) {
			return Optional.empty();
		}
		return Optional.of(asStringSet(payload));
	}

	public void delete(UUID actorUserId, String roleCode) {
		redis.delete(AuthRedisKeys.rolePermissionChangePending(actorUserId, roleCode));
	}

	private Set<String> normalize(Set<String> permissionCodes) {
		return permissionCodes.stream()
				.map(String::trim)
				.filter(code -> !code.isEmpty())
				.collect(Collectors.toCollection(TreeSet::new));
	}

	private Set<String> asStringSet(Object payload) {
		if (payload instanceof Collection<?> values) {
			Set<String> codes = values.stream()
					.map(String::valueOf)
					.collect(Collectors.toCollection(TreeSet::new));
			if (codes.isEmpty()) {
				throw new IllegalStateException("Pending role permission change payload is empty");
			}
			return Set.copyOf(codes);
		}
		throw new IllegalStateException("Unexpected pending role permission change payload type: " + payload.getClass());
	}
}
