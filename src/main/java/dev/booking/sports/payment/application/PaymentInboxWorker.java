package dev.booking.sports.payment.application;

import java.time.Instant;

import org.springframework.stereotype.Service;
import dev.booking.sports.payment.config.PaymentInboxProperties;
import dev.booking.sports.payment.domain.repository.PaymentInboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentInboxWorker {

    private final PaymentInboxRepository repository;
    private final PaymentInboxProcessor processor;
    private final PaymentInboxFailures failures;
    private final PaymentInboxProperties properties;

    public void runBatch() {
        for (int index = 0; index < properties.batchSize(); index++) {
            var claim = repository.claimNext(Instant.now(), properties.leaseDuration());
            if (claim.isEmpty()) {
                return;
            }
            try {
                processor.process(claim.get());
            } catch (RuntimeException error) {
                log.warn("Payment inbox {} failed: {}", claim.get().id(), error.getClass().getSimpleName());
                failures.record(claim.get(), error);
            }
        }
    }
}
