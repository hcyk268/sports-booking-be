package dev.booking.sports.payment.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.booking.sports.payment.config.PaymentInboxProperties;
import dev.booking.sports.payment.domain.repository.PaymentInboxRepository;
import dev.booking.sports.payment.domain.repository.PaymentInboxRepository.Claim;

class PaymentInboxWorkerTest {

    private final PaymentInboxRepository repository = mock(PaymentInboxRepository.class);
    private final PaymentInboxProcessor processor = mock(PaymentInboxProcessor.class);
    private final PaymentInboxFailures failures = mock(PaymentInboxFailures.class);
    private final Duration lease = Duration.ofSeconds(60);
    private final Claim claim = new Claim(UUID.randomUUID(), UUID.randomUUID());
    private PaymentInboxWorker worker;

    @BeforeEach
    void setUp() {
        worker = new PaymentInboxWorker(repository, processor, failures,
                new PaymentInboxProperties(3, lease, Duration.ofSeconds(15), 8));
    }

    @Test
    void stopsBatchAsSoonAsNoClaimableInboxRemains() {
        when(repository.claimNext(any(Instant.class), eq(lease)))
                .thenReturn(Optional.of(claim), Optional.empty());

        worker.runBatch();

        verify(processor).process(claim);
        verify(repository, times(2)).claimNext(any(Instant.class), eq(lease));
    }

    @Test
    void recordsAProcessorFailureAndContinuesTheBatch() {
        Claim next = new Claim(UUID.randomUUID(), UUID.randomUUID());
        when(repository.claimNext(any(Instant.class), eq(lease)))
                .thenReturn(Optional.of(claim), Optional.of(next), Optional.empty());
        var error = new IllegalStateException("database unavailable");
        doThrow(error).when(processor).process(claim);

        worker.runBatch();

        verify(failures).record(claim, error);
        verify(processor).process(next);
    }
}
