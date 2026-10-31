package dev.booking.sports.payment.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import dev.booking.sports.payment.domain.enums.PaymentEnvironment;
import dev.booking.sports.payment.domain.model.PaymentAttempt.CheckoutField;

final class PaymentFixtures {
    static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    static final String HASH = "a".repeat(64);
    static final BigDecimal AMOUNT = new BigDecimal("300000");

    static Payment payment() {
        Payment payment = Payment.pending(UUID.randomUUID(), AMOUNT, "VND", NOW.plusSeconds(600), NOW);
        payment.setId(UUID.randomUUID());
        return payment;
    }

    static PaymentAttempt.CheckoutSpec spec() {
        return new PaymentAttempt.CheckoutSpec(PaymentEnvironment.SANDBOX, "merchant", "invoice",
                "BANK_TRANSFER", List.of(new CheckoutField("order_amount", "300000"),
                    new CheckoutField("signature", "secret-signature")), "checkout-key", HASH);
    }

    static PaymentAttempt attempt(Payment payment) {
        PaymentAttempt attempt = PaymentAttempt.prepare(payment, 1, spec(), NOW);
        attempt.setId(UUID.randomUUID());
        return attempt;
    }

    static PaymentTransaction receipt(PaymentAttempt attempt, String amount) {
        PaymentTransaction transaction = PaymentTransaction.record(new PaymentTransaction.Receipt(
                PaymentEnvironment.SANDBOX, "merchant", UUID.randomUUID().toString(), null, null,
                "invoice", "PAYMENT", "APPROVED", "BANK_TRANSFER", new BigDecimal(amount),
                "VND", NOW.plusSeconds(60)), NOW.plusSeconds(61));
        transaction.setId(UUID.randomUUID());
        if (attempt != null) {
            transaction.match(attempt);
        }
        return transaction;
    }
}
