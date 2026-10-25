package dev.booking.sports.booking.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import dev.booking.sports.booking.api.error.BookingErrorCode;
import dev.booking.sports.booking.config.BookingHoldProperties;
import dev.booking.sports.shared.exception.ApiException;

@ExtendWith(MockitoExtension.class)
class BookingLockManagerTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> values;

    private BookingLockManager manager;

    @BeforeEach
    void setUp() {
        manager = new BookingLockManager(redis, new BookingHoldProperties(
                Duration.ofMinutes(10), Duration.ofSeconds(30)));
    }

    @Test
    void acquiresDistinctSlotKeysInStableOrder() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(30))))
                .thenReturn(true);

        try (var ignored = manager.acquireSlots(List.of("slot-b", "slot-a", "slot-a"))) {
            ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
            verify(values, times(2)).setIfAbsent(
                    keys.capture(), anyString(), eq(Duration.ofSeconds(30)));
            assertThat(keys.getAllValues()).containsExactly("slot-a", "slot-b");
        }

        verify(redis, times(2)).execute(
                any(RedisScript.class), any(List.class), anyString());
    }

    @Test
    void releasesEarlierLockWhenLaterKeyIsBusy() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(30))))
                .thenAnswer(invocation -> "slot-a".equals(invocation.getArgument(0)));

        assertThatThrownBy(() -> manager.acquireSlots(List.of("slot-b", "slot-a")))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(BookingErrorCode.BOOKING_SLOT_UNAVAILABLE));

        verify(redis).execute(
                any(RedisScript.class), eq(List.of("slot-a")), anyString());
    }
}
