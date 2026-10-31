package dev.booking.sports.payment.application;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.booking.sports.payment.config.PaymentInboxProperties;
import dev.booking.sports.payment.domain.event.PaymentOutboxMessageTypes;
import dev.booking.sports.payment.domain.event.PaymentReviewRequiredEvent;
import dev.booking.sports.payment.domain.repository.PaymentInboxRepository;
import dev.booking.sports.payment.domain.repository.PaymentInboxRepository.Claim;
import dev.booking.sports.shared.outbox.OutboxService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentInboxFailures {

    private final PaymentInboxRepository inboxes;
    private final PaymentInboxProperties properties;
    private final OutboxService outbox;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Claim claim, RuntimeException error) {
        var inbox = inboxes.findForUpdate(claim.id()).orElse(null);
        Instant now = Instant.now();
        if (inbox == null || !inbox.ownsLiveLease(claim.token(), now)) {
            return;
        }

        String reason = error.getClass().getSimpleName();
        if (inbox.getAttempts() >= properties.maxAttempts()) {
            inbox.fail(claim.token(), now, "RETRY_EXHAUSTED: " + reason);
            outbox.enqueue("PAYMENT_INBOX", inbox.getId(), PaymentOutboxMessageTypes.REVIEW_REQUIRED,
                    PaymentOutboxMessageTypes.VERSION,
                    new PaymentReviewRequiredEvent(inbox.getId(), null, null, inbox.getInvoiceNumber(),
                            "RETRY_EXHAUSTED", now));
            return;
        }

        long multiplier = 1L << Math.min(inbox.getAttempts() - 1, 6);
        var delay = properties.retryDelay().multipliedBy(multiplier);
        inbox.retry(claim.token(), now, now.plus(delay), reason);
    }
}
