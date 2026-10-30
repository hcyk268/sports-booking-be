package dev.booking.sports.payment.domain.model;

import static dev.booking.sports.payment.domain.model.PaymentFixtures.*;
import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import dev.booking.sports.payment.domain.enums.PaymentResolutionStatus;
import dev.booking.sports.payment.domain.enums.PaymentStatus;

class PaymentTest {
    @Test
    void settlesOneReceiptAndReplaysWithoutDuplicatingHistory() {
        Payment payment = payment();
        PaymentTransaction transaction = receipt(attempt(payment), "300000.00");
        payment.settle(transaction, NOW.plusSeconds(90));
        payment.settle(transaction, NOW.plusSeconds(900));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getSettledTransactionId()).isEqualTo(transaction.getId());
        assertThat(transaction.getResolutionStatus()).isEqualTo(PaymentResolutionStatus.APPLIED);
        assertThat(payment.getStatusHistory()).hasSize(2);
        assertThatThrownBy(() -> payment.getStatusHistory().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsASecondReceiptWithoutLosingIt() {
        Payment payment = payment();
        PaymentAttempt attempt = attempt(payment);
        payment.settle(receipt(attempt, "300000"), NOW.plusSeconds(90));
        PaymentTransaction extra = receipt(attempt, "300000");
        assertThatThrownBy(() -> payment.settle(extra, NOW.plusSeconds(100)))
                .isInstanceOf(IllegalStateException.class);
        extra.requireReview("Extra payment");
        assertThat(extra.getAmount()).isEqualByComparingTo(AMOUNT);
        assertThat(extra.getResolutionStatus()).isEqualTo(PaymentResolutionStatus.REVIEW_REQUIRED);
    }

    @Test
    void rejectsSettlementAtDeadlineEvenBeforeExpiryJobRuns() {
        Payment payment = payment();
        PaymentTransaction transaction = receipt(attempt(payment), "300000");
        assertThatThrownBy(() -> payment.settle(transaction, NOW.plusSeconds(600)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(transaction.getResolutionStatus()).isEqualTo(PaymentResolutionStatus.PENDING);
        payment.expire(NOW.plusSeconds(600));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
    }

    @Test
    void rejectsExpiryBeforeDeadline() {
        Payment payment = payment();
        assertThatThrownBy(() -> payment.expire(NOW.plusSeconds(599))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsWrongAmountWrongPaymentAndUnmatchedReceipts() {
        Payment payment = payment();
        assertThatThrownBy(() -> payment.settle(receipt(attempt(payment), "299999"), NOW.plusSeconds(90)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> payment.settle(receipt(attempt(payment()), "300000"), NOW.plusSeconds(90)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> payment.settle(receipt(null, "300000"), NOW.plusSeconds(90)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void cancelledPaymentCannotSettleAndPaidPaymentCannotBeCancelled() {
        Payment payment = payment();
        PaymentTransaction transaction = receipt(attempt(payment), "300000");
        payment.cancel(UUID.randomUUID(), "Customer cancellation");
        assertThatThrownBy(() -> payment.settle(transaction, NOW.plusSeconds(90)))
                .isInstanceOf(IllegalStateException.class);
        Payment paid = payment();
        paid.settle(receipt(attempt(paid), "300000"), NOW.plusSeconds(90));
        assertThatThrownBy(() -> paid.cancel(UUID.randomUUID(), "Customer cancellation"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsUnsupportedCurrencyFractionalAndOversizedAmount() {
        assertThatThrownBy(() -> Payment.pending(UUID.randomUUID(), AMOUNT, "USD", NOW.plusSeconds(600), NOW))
                .isInstanceOf(IllegalArgumentException.class);
        for (String value : new String[] {"0", "-1", "1.01", "10000000000000"}) {
            assertThatThrownBy(() -> Payment.pending(UUID.randomUUID(), new BigDecimal(value), "VND",
                    NOW.plusSeconds(600), NOW)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void reversalKeepsSettlementButRequiresReviewAndCannotSettleAnotherBooking() {
        Payment payment = payment();
        PaymentTransaction transaction = receipt(attempt(payment), "300000");
        payment.settle(transaction, NOW.plusSeconds(90));
        transaction.recordProviderStatus("VOIDED");
        assertThat(transaction.getResolutionStatus()).isEqualTo(PaymentResolutionStatus.REVIEW_REQUIRED);
        assertThat(payment.getSettledTransactionId()).isEqualTo(transaction.getId());
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThatThrownBy(() -> transaction.match(attempt(payment())))
                .isInstanceOf(IllegalStateException.class);
    }
}
