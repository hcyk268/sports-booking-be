package dev.booking.sports.payment.infrastructure.inbox;

import static org.assertj.core.api.Assertions.*;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import dev.booking.sports.payment.domain.enums.PaymentInboxStatus;
import dev.booking.sports.payment.domain.model.PaymentWebhookInbox;
import org.junit.jupiter.api.Test;
import dev.booking.sports.payment.domain.enums.PaymentEnvironment;

class PaymentWebhookInboxTest {
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

    @Test
    void leaseTakeoverFencesOldWorker() {
        PaymentWebhookInbox inbox = inbox();
        UUID oldToken = inbox.claim(NOW, Duration.ofSeconds(30));
        assertThatThrownBy(() -> inbox.claim(NOW.plusSeconds(29), Duration.ofSeconds(30)))
                .isInstanceOf(IllegalStateException.class);
        UUID newToken = inbox.claim(NOW.plusSeconds(30), Duration.ofSeconds(30));
        assertThat(newToken).isNotEqualTo(oldToken);
        assertThatThrownBy(() -> inbox.markProcessed(oldToken, NOW.plusSeconds(31)))
                .isInstanceOf(IllegalStateException.class);
        inbox.markProcessed(newToken, NOW.plusSeconds(31));
        assertThat(inbox.getStatus()).isEqualTo(PaymentInboxStatus.PROCESSED);
        assertThat(inbox.getAttempts()).isEqualTo(2);
        assertThat(inbox.getLockedUntil()).isNull();
        assertThat(inbox.getLockToken()).isNull();
    }

    @Test
    void expiredWorkerCannotCompleteEvenWithoutTakeover() {
        PaymentWebhookInbox inbox = inbox();
        UUID token = inbox.claim(NOW, Duration.ofSeconds(30));
        assertThatThrownBy(() -> inbox.markProcessed(token, NOW.plusSeconds(30)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void retryRespectsAvailableTimeAndReleasesLease() {
        PaymentWebhookInbox inbox = inbox();
        UUID token = inbox.claim(NOW, Duration.ofSeconds(30));
        inbox.retry(token, NOW.plusSeconds(1), NOW.plusSeconds(60), "Database unavailable");
        assertThat(inbox.getLockToken()).isNull();
        assertThatThrownBy(() -> inbox.claim(NOW.plusSeconds(59), Duration.ofSeconds(30)))
                .isInstanceOf(IllegalStateException.class);
        inbox.claim(NOW.plusSeconds(60), Duration.ofSeconds(30));
        assertThat(inbox.getAttempts()).isEqualTo(2);
    }

    @Test
    void invalidRetryDoesNotPartiallyMutateInbox() {
        PaymentWebhookInbox inbox = inbox();
        UUID token = inbox.claim(NOW, Duration.ofSeconds(30));
        assertThatThrownBy(() -> inbox.retry(token, NOW.plusSeconds(1), NOW, "Failure"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(inbox.getStatus()).isEqualTo(PaymentInboxStatus.PROCESSING);
        assertThat(inbox.getLockToken()).isEqualTo(token);
    }

    @Test
    void rejectsNonObjectPayloadAndDoesNotExposePayloadInRecordToString() {
        assertThatThrownBy(() -> notification("[]")).isInstanceOf(IllegalArgumentException.class);
        assertThat(notification("{\"sensitive\":\"value\"}").toString()).doesNotContain("sensitive");
    }

    private PaymentWebhookInbox inbox() {
        return PaymentWebhookInbox.receive(notification("{}"), NOW);
    }

    private PaymentWebhookInbox.Notification notification(String payload) {
        return new PaymentWebhookInbox.Notification(PaymentEnvironment.SANDBOX, "merchant", "ORDER_PAID",
                "ORDER_PAID:transaction", "a".repeat(64), payload, "transaction", "invoice", NOW);
    }
}
