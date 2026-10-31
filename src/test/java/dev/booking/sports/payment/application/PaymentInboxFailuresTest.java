package dev.booking.sports.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import dev.booking.sports.payment.config.PaymentInboxProperties;
import dev.booking.sports.payment.domain.enums.PaymentEnvironment;
import dev.booking.sports.payment.domain.enums.PaymentInboxStatus;
import dev.booking.sports.payment.domain.event.PaymentOutboxMessageTypes;
import dev.booking.sports.payment.domain.model.PaymentWebhookInbox;
import dev.booking.sports.payment.domain.repository.PaymentInboxRepository;
import dev.booking.sports.payment.domain.repository.PaymentInboxRepository.Claim;
import dev.booking.sports.shared.outbox.OutboxService;

class PaymentInboxFailuresTest {

    private final PaymentInboxRepository inboxes = mock(PaymentInboxRepository.class);
    private final OutboxService outbox = mock(OutboxService.class);
    private final Instant now = Instant.now();
    private PaymentWebhookInbox inbox;
    private Claim claim;
    private PaymentInboxFailures failures;

    @BeforeEach
    void setUp() {
        var notification = new PaymentWebhookInbox.Notification(PaymentEnvironment.SANDBOX, "merchant", "ORDER_PAID",
                "key", "a".repeat(64), "{}", "transaction", "invoice", now);
        inbox = PaymentWebhookInbox.receive(notification, now);
        ReflectionTestUtils.setField(inbox, "id", UUID.randomUUID());
        claim = new Claim(inbox.getId(), inbox.claim(Instant.now(), Duration.ofMinutes(5)));
        when(inboxes.findForUpdate(inbox.getId())).thenReturn(Optional.of(inbox));
        failures = new PaymentInboxFailures(inboxes,
                new PaymentInboxProperties(25, Duration.ofMinutes(5), Duration.ofSeconds(15), 2), outbox);
    }

    @Test
    void retriesWithSanitizedErrorThenFailsAndRaisesReview() {
        failures.record(claim, new IllegalStateException("private payload"));

        assertThat(inbox.getStatus()).isEqualTo(PaymentInboxStatus.PENDING);
        assertThat(inbox.getLastError()).isEqualTo("IllegalStateException");
        verifyNoInteractions(outbox);

        claim = new Claim(inbox.getId(), inbox.claim(inbox.getAvailableAt(), Duration.ofMinutes(5)));
        failures.record(claim, new IllegalStateException("private payload"));

        assertThat(inbox.getStatus()).isEqualTo(PaymentInboxStatus.FAILED);
        assertThat(inbox.getLastError()).doesNotContain("private payload");
        verify(outbox).enqueue(eq("PAYMENT_INBOX"), eq(inbox.getId()),
                eq(PaymentOutboxMessageTypes.REVIEW_REQUIRED), eq(PaymentOutboxMessageTypes.VERSION), any());
    }

    @Test
    void staleClaimCannotScheduleRetry() {
        failures.record(new Claim(inbox.getId(), UUID.randomUUID()), new IllegalStateException());

        assertThat(inbox.getStatus()).isEqualTo(PaymentInboxStatus.PROCESSING);
        verifyNoInteractions(outbox);
    }
}
