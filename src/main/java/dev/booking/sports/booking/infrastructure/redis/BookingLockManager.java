package dev.booking.sports.booking.infrastructure.redis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import dev.booking.sports.booking.api.error.BookingErrorCode;
import dev.booking.sports.booking.config.BookingHoldProperties;
import dev.booking.sports.shared.exception.ApiException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingLockManager {

    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
            """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """,
            Long.class);

    private final StringRedisTemplate redis;
    private final BookingHoldProperties properties;

    public LockHandle acquireIdempotency(UUID customerId, String key) {
        return acquire(
                List.of("booking:idem:" + customerId + ":" + key),
                BookingErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS);
    }

    public LockHandle acquireSlots(List<String> keys) {
        return acquire(keys, BookingErrorCode.BOOKING_SLOT_UNAVAILABLE);
    }

    private LockHandle acquire(List<String> keys, BookingErrorCode conflict) {
        List<String> ordered = keys.stream()
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        String token = UUID.randomUUID().toString();
        List<String> acquired = new ArrayList<>(ordered.size());
        try {
            for (String key : ordered) {
                if (!Boolean.TRUE.equals(redis.opsForValue()
                        .setIfAbsent(key, token, properties.lockTtl()))) {
                    throw new ApiException(conflict);
                }
                acquired.add(key);
            }
            return new LockHandle(acquired, token);
        } catch (RuntimeException exception) {
            release(acquired, token);
            throw exception;
        }
    }

    private void release(List<String> keys, String token) {
        for (int index = keys.size() - 1; index >= 0; index--) {
            try {
                redis.execute(RELEASE, List.of(keys.get(index)), token);
            } catch (RuntimeException exception) {
                // The TTL releases the key if Redis is unavailable during cleanup.
                log.warn("Could not release booking lock {}", keys.get(index), exception);
            }
        }
    }

    public final class LockHandle implements AutoCloseable {
        private final List<String> keys;
        private final String token;
        private boolean closed;

        private LockHandle(List<String> keys, String token) {
            this.keys = List.copyOf(keys);
            this.token = token;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                release(keys, token);
            }
        }
    }
}
