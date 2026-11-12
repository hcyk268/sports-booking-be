package dev.booking.sports.notification.delivery;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import dev.booking.sports.notification.email.EmailDeliveryException;
import dev.booking.sports.support.BaseIntegrationTest;
import dev.booking.sports.support.IntegrationTest;

@IntegrationTest
@Timeout(30)
class EmailDeliveryDedupServiceTest extends BaseIntegrationTest {
    @Autowired private EmailDeliveryDedupService deliveryDedupService;
    @Autowired private JdbcTemplate jdbc;

    @Test void successfulDeliveryIsCommittedAndDuplicatesAreSkipped() {
        UUID id = UUID.randomUUID();
        var sends = new AtomicInteger();
        assertThat(deliveryDedupService.deliverOnce(id, sends::incrementAndGet)).isTrue();
        assertThat(deliveryDedupService.deliverOnce(id, sends::incrementAndGet)).isFalse();
        assertThat(sends.get()).isEqualTo(1);
    }

    @Test void deliveryFailureRollsBackAndAllowsReplay() {
        assertRollbackAndReplay(new EmailDeliveryException("SendGrid failed"));
    }

    @Test void unexpectedFailureRollsBackAndAllowsRedelivery() {
        assertRollbackAndReplay(new IllegalStateException("unexpected failure"));
    }

    private void assertRollbackAndReplay(RuntimeException failure) {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> deliveryDedupService.deliverOnce(id, () -> { throw failure; }))
                .isSameAs(failure);
        assertThat(count(id)).isZero();
        var sends = new AtomicInteger();
        assertThat(deliveryDedupService.deliverOnce(id, sends::incrementAndGet)).isTrue();
        assertThat(sends.get()).isEqualTo(1);
        assertThat(count(id)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrentDeliveryWaitsForCommitOrTakesOverAfterRollback(boolean failFirst) throws Exception {
        UUID id = UUID.randomUUID();
        var firstSending = new CountDownLatch(1);
        var finishFirst = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var secondSends = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> deliveryDedupService.deliverOnce(id, () -> {
                firstSending.countDown();
                await(finishFirst);
                if (failFirst) throw new IllegalStateException("first delivery failed");
            }));
            try {
                assertThat(firstSending.await(10, TimeUnit.SECONDS)).isTrue();
                // A separate transaction cannot see the dedup ID before send succeeds.
                assertThat(count(id)).isZero();
                Future<Boolean> second = executor.submit(() -> {
                    secondStarted.countDown();
                    return deliveryDedupService.deliverOnce(id, secondSends::incrementAndGet);
                });
                assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                finishFirst.countDown();
                if (failFirst) {
                    assertThatThrownBy(() -> first.get(10, TimeUnit.SECONDS))
                            .isInstanceOf(ExecutionException.class)
                            .hasCauseInstanceOf(IllegalStateException.class);
                } else {
                    assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
                }
                assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(failFirst);
                assertThat(secondSends.get()).isEqualTo(failFirst ? 1 : 0);
                assertThat(count(id)).isEqualTo(1);
            } finally {
                finishFirst.countDown();
            }
        }
    }

    private int count(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM processed_email_deliveries WHERE outbox_event_id = ?",
                Integer.class, id);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test latch timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
