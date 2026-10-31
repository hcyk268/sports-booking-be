package dev.booking.sports.payment.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
import dev.booking.sports.payment.application.PaymentInboxWorker;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.payment.inbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PaymentInboxSchedulingConfig {

    private final PaymentInboxWorker worker;

    @Scheduled(fixedDelayString = "${app.payment.inbox.poll-interval:1s}")
    public void processInbox() {
        worker.runBatch();
    }
}
