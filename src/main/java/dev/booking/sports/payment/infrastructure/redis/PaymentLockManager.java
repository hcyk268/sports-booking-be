package dev.booking.sports.payment.infrastructure.redis;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import dev.booking.sports.payment.api.error.PaymentErrorCode;
import dev.booking.sports.shared.exception.ApiException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentLockManager {

    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
            """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """,
            Long.class);

    private final StringRedisTemplate redis;

    public LockHandle acquireIdempotency(UUID customerId, String idempotencyKey) {
        Objects.requireNonNull(customerId, "customerId must not be null");
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new IllegalArgumentException("idempotencyKey must contain 1-100 characters");
        }
        String key = "payment:idem:" + customerId + ":" + idempotencyKey;
        String token = UUID.randomUUID().toString();
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, token, LOCK_TTL))) {
            throw new ApiException(PaymentErrorCode.PAYMENT_IDEMPOTENCY_REQUEST_IN_PROGRESS);
        }
        return new LockHandle(key, token);
    }

    private void release(String key, String token) {
        try {
            redis.execute(RELEASE, List.of(key), token);
        } catch (RuntimeException exception) {
            // The TTL releases the key if Redis is unavailable during cleanup.
            log.warn("Could not release payment lock {}", key, exception);
        }
    }

    public final class LockHandle implements AutoCloseable {
        private final String key;
        private final String token;
        private boolean closed;

        private LockHandle(String key, String token) {
            this.key = key;
            this.token = token;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                release(key, token);
            }
        }
    }

}
