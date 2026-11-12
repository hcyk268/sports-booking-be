package dev.booking.sports.shared.outbox;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.TransactionDefinition;
import dev.booking.sports.shared.config.OutboxProperties;

class OutboxRetryTest {
    @Test void backoffDoublesAndCapsWithoutOverflow() {
        var props = new OutboxProperties(Duration.ofSeconds(1), 25, 10, Duration.ofSeconds(5), Duration.ofMinutes(5));
        assertThat(props.retryDelay(0)).isEqualTo(Duration.ofSeconds(5));
        assertThat(props.retryDelay(1)).isEqualTo(Duration.ofSeconds(10));
        assertThat(props.retryDelay(6)).isEqualTo(Duration.ofMinutes(5));
        assertThat(props.retryDelay(Integer.MAX_VALUE)).isEqualTo(Duration.ofMinutes(5));
    }

    @Test void failedPublicationIsDelayedThenBecomesFailedAtLimit() throws Exception {
        var repository = mock(OutboxRepository.class);
        var handler = mock(OutboxPublicationHandler.class);
        var message = new OutboxEvent("TEST", UUID.randomUUID().toString(), "test.event", 1, "{}");
        when(repository.lockPendingBatch(1)).thenReturn(List.of(message));
        when(handler.supports("test.event")).thenReturn(true);
        doThrow(new IllegalStateException("broker down")).when(handler).publish(message);
        var props = new OutboxProperties(Duration.ofSeconds(1), 1, 2, Duration.ofSeconds(5), Duration.ofMinutes(5));
        var tx = new AbstractPlatformTransactionManager() {
            protected Object doGetTransaction() { return new Object(); }
            protected void doBegin(Object t, TransactionDefinition d) {}
            protected void doCommit(DefaultTransactionStatus s) {}
            protected void doRollback(DefaultTransactionStatus s) {}
        };
        var relay = new OutboxRelay(repository, List.of(handler), props, tx);
        Instant before = Instant.now();
        relay.dispatchPendingMessages();
        assertThat(message.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(message.getAttempts()).isEqualTo(1);
        assertThat(message.getAvailableAt()).isAfterOrEqualTo(before.plusSeconds(5));
        relay.dispatchPendingMessages();
        assertThat(message.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(message.getAttempts()).isEqualTo(2);
    }
}
