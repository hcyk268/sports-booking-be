package dev.booking.sports.payment.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import dev.booking.sports.payment.api.error.PaymentErrorCode;
import dev.booking.sports.shared.exception.ApiException;

@ExtendWith(MockitoExtension.class)
class PaymentLockManagerTest {
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final Duration TTL = Duration.ofSeconds(30);

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> values;

    private PaymentLockManager manager;

    @BeforeEach
    void setUp() {
        manager = new PaymentLockManager(redis);
    }

    @Test
    void acquiresWithTtlAndReleasesExactlyOnceUsingTheAcquisitionToken() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), eq(TTL))).thenReturn(true);

        var handle = manager.acquireIdempotency(CUSTOMER_ID, "private-request-key");
        var key = ArgumentCaptor.forClass(String.class);
        var token = ArgumentCaptor.forClass(String.class);
        verify(values).setIfAbsent(key.capture(), token.capture(), eq(TTL));
        assertThat(key.getValue()).isEqualTo("payment:idem:" + CUSTOMER_ID + ":private-request-key");
        assertThat(UUID.fromString(token.getValue())).isNotNull();

        handle.close();
        handle.close();
        verify(redis, times(1)).execute(any(RedisScript.class), eq(List.of(key.getValue())), eq(token.getValue()));
    }

    @Test
    void sameRequestUsesStableKeyButEachAcquisitionHasANewOwnerToken() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), eq(TTL))).thenReturn(true);
        try (var first = manager.acquireIdempotency(CUSTOMER_ID, "request")) {
            first.close();
            try (var second = manager.acquireIdempotency(CUSTOMER_ID, "request")) {
                var keys = ArgumentCaptor.forClass(String.class);
                var tokens = ArgumentCaptor.forClass(String.class);
                verify(values, times(2)).setIfAbsent(keys.capture(), tokens.capture(), eq(TTL));
                assertThat(keys.getAllValues().get(0)).isEqualTo(keys.getAllValues().get(1));
                assertThat(tokens.getAllValues().get(0)).isNotEqualTo(tokens.getAllValues().get(1));
            }
        }
    }

    @Test
    void scopesLocksByCustomerAndPreservesExactRequestKeyIdentity() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), eq(TTL))).thenReturn(true);
        try (var first = manager.acquireIdempotency(CUSTOMER_ID, "request");
             var otherCustomer = manager.acquireIdempotency(UUID.randomUUID(), "request");
             var otherKey = manager.acquireIdempotency(CUSTOMER_ID, " request")) {
            var keys = ArgumentCaptor.forClass(String.class);
            verify(values, times(3)).setIfAbsent(keys.capture(), anyString(), eq(TTL));
            assertThat(keys.getAllValues()).doesNotHaveDuplicates();
        }
    }

    @Test
    void busyLockReturnsConflictAndDoesNotReleaseAnotherOwner() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), eq(TTL))).thenReturn(false);
        assertThatThrownBy(() -> manager.acquireIdempotency(CUSTOMER_ID, "request"))
                .isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(PaymentErrorCode.PAYMENT_IDEMPOTENCY_REQUEST_IN_PROGRESS));
        verify(redis, never()).execute(any(RedisScript.class), anyList(), anyString());
    }

    @Test
    void nullAcquisitionResultDoesNotAllowWorkToProceed() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), eq(TTL))).thenReturn(null);
        assertThatThrownBy(() -> manager.acquireIdempotency(CUSTOMER_ID, "request"))
                .isInstanceOf(ApiException.class);
        verify(redis, never()).execute(any(RedisScript.class), anyList(), anyString());
    }

    @Test
    void redisAcquisitionFailureIsPropagatedRatherThanReportedAsAnExistingRequest() {
        var failure = new RedisConnectionFailureException("Redis unavailable");
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), eq(TTL))).thenThrow(failure);
        assertThatThrownBy(() -> manager.acquireIdempotency(CUSTOMER_ID, "request")).isSameAs(failure);
        verify(redis, never()).execute(any(RedisScript.class), anyList(), anyString());
    }

    @Test
    void releaseFailureDoesNotMaskTheOriginalBusinessFailure() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), eq(TTL))).thenReturn(true);
        when(redis.execute(any(RedisScript.class), anyList(), anyString()))
                .thenThrow(new RedisConnectionFailureException("Redis unavailable during release"));
        var businessFailure = new IllegalStateException("Transaction failed");

        assertThatThrownBy(() -> {
            try (var ignored = manager.acquireIdempotency(CUSTOMER_ID, "request")) {
                throw businessFailure;
            }
        }).isSameAs(businessFailure);
    }

    @Test
    void rejectsMissingCustomerAndInvalidKeysBeforeTouchingRedis() {
        assertThatThrownBy(() -> manager.acquireIdempotency(null, "request")).isInstanceOf(NullPointerException.class);
        for (String key : new String[] {null, "", " \t", "a".repeat(101)}) {
            assertThatThrownBy(() -> manager.acquireIdempotency(CUSTOMER_ID, key))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(redis, values);
    }
}
