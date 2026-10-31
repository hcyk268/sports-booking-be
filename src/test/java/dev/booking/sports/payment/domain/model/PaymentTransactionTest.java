package dev.booking.sports.payment.domain.model;

import static dev.booking.sports.payment.domain.model.PaymentFixtures.*;
import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import dev.booking.sports.payment.domain.enums.*;

class PaymentTransactionTest {
    @Test
    void retainsForeignCurrencyReceiptButRefusesToSettleVndPayment() {
        Payment payment = payment();
        PaymentTransaction transaction = transaction(PaymentEnvironment.SANDBOX, "APPROVED", "USD");
        transaction.match(attempt(payment));
        assertThatThrownBy(() -> payment.settle(transaction, NOW.plusSeconds(90)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(transaction.getCurrency()).isEqualTo("USD");
        assertThat(transaction.getResolutionStatus()).isEqualTo(PaymentResolutionStatus.PENDING);
    }

    @Test
    void preservesUnknownGatewayStatusWithoutTreatingItAsACollection() {
        Payment payment = payment();
        PaymentTransaction transaction = transaction(PaymentEnvironment.SANDBOX, "NEW_PROVIDER_STATUS", "VND");
        transaction.match(attempt(payment));
        assertThatThrownBy(() -> payment.settle(transaction, NOW.plusSeconds(90)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(transaction.getProviderStatus()).isEqualTo("NEW_PROVIDER_STATUS");
    }

    @Test
    void refusesMatchingAcrossEnvironments() {
        PaymentTransaction transaction = transaction(PaymentEnvironment.PRODUCTION, "APPROVED", "VND");
        assertThatThrownBy(() -> transaction.match(attempt(payment())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(transaction.getPaymentId()).isNull();
        assertThat(transaction.getPaymentAttemptId()).isNull();
    }

    @Test
    void reviewResolutionDoesNotMakeAnExtraReceiptEligibleForSettlement() {
        Payment payment = payment();
        PaymentTransaction transaction = receipt(attempt(payment), "300000");
        transaction.requireReview("Late receipt");
        transaction.resolve(UUID.randomUUID(), "Manually refunded", NOW.plusSeconds(90));
        assertThat(transaction.getResolutionStatus()).isEqualTo(PaymentResolutionStatus.RESOLVED);
        assertThatThrownBy(() -> payment.settle(transaction, NOW.plusSeconds(100)))
                .isInstanceOf(IllegalStateException.class);
    }

    private PaymentTransaction transaction(PaymentEnvironment environment, String status, String currency) {
        PaymentTransaction transaction = PaymentTransaction.record(new PaymentTransaction.Receipt(
                environment, "merchant", UUID.randomUUID().toString(), null, null, "invoice",
                "PAYMENT", status, null, new BigDecimal("300000"), currency, NOW), NOW);
        transaction.setId(UUID.randomUUID());
        return transaction;
    }
}
