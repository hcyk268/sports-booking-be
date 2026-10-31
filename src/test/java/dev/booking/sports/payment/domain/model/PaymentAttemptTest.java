package dev.booking.sports.payment.domain.model;

import static dev.booking.sports.payment.domain.model.PaymentFixtures.*;
import static org.assertj.core.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import dev.booking.sports.payment.domain.enums.*;
import dev.booking.sports.payment.domain.model.PaymentAttempt.CheckoutField;

class PaymentAttemptTest {
    @Test
    void keepsCheckoutOrderAndDefensivelyCopiesFields() {
        var fields = new ArrayList<>(spec().fields());
        var checkout = new PaymentAttempt.CheckoutSpec(PaymentEnvironment.SANDBOX, "merchant", "invoice",
                null, fields, "key", HASH);
        PaymentAttempt attempt = PaymentAttempt.prepare(payment(), 1, checkout, NOW);
        fields.clear();
        assertThat(attempt.getCheckoutPayload()).extracting(CheckoutField::name)
                .containsExactly("order_amount", "signature");
        assertThatThrownBy(() -> attempt.getCheckoutPayload().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(checkout.toString()).doesNotContain("secret-signature");
        assertThat(attempt.getCheckoutPayload().toString()).doesNotContain("secret-signature");
    }

    @Test
    void unknownOutcomeBlocksResumeUntilReconciled() {
        PaymentAttempt attempt = attempt(payment());
        attempt.markUnknown("Timeout", NOW.plusSeconds(30));
        assertThat(attempt.getStatus().isActive()).isTrue();
        assertThat(attempt.canResumeCheckout(NOW.plusSeconds(10))).isFalse();
        attempt.markPending("order-1", "AUTHENTICATION_NOT_NEEDED");
        assertThat(attempt.canResumeCheckout(NOW.plusSeconds(31))).isTrue();
        assertThat(attempt.canResumeCheckout(NOW.plusSeconds(600))).isFalse();
    }

    @Test
    void recordsLateGatewaySuccessWithoutExtendingLocalDeadline() {
        Payment payment = payment();
        PaymentAttempt attempt = attempt(payment);
        attempt.expire(NOW.plusSeconds(600));
        attempt.recordGatewayOutcome(PaymentAttemptStatus.SUCCEEDED, "order-1", "CAPTURED");
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.SUCCEEDED);
        assertThat(attempt.getExpiresAt()).isEqualTo(payment.getExpiresAt());
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void refusesConflictingOrderIdentityWithoutChangingState() {
        PaymentAttempt attempt = attempt(payment());
        attempt.markPending("order-1", "AUTHENTICATION_NOT_NEEDED");
        assertThatThrownBy(() -> attempt.recordGatewayOutcome(PaymentAttemptStatus.SUCCEEDED, "order-2", "CAPTURED"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.PENDING);
        assertThat(attempt.getProviderOrderId()).isEqualTo("order-1");
    }

    @Test
    void cannotTurnCollectedAttemptIntoFailedOrPending() {
        PaymentAttempt attempt = attempt(payment());
        attempt.recordGatewayOutcome(PaymentAttemptStatus.SUCCEEDED, "order-1", "CAPTURED");
        assertThatThrownBy(() -> attempt.recordGatewayOutcome(PaymentAttemptStatus.FAILED, "order-1", "FAILED"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> attempt.markPending("order-1", "AUTHENTICATION_NOT_NEEDED"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsDuplicateCheckoutFieldsAndExpiredPreparation() {
        assertThatThrownBy(() -> new PaymentAttempt.CheckoutSpec(PaymentEnvironment.SANDBOX, "merchant",
                "invoice", null, List.of(new CheckoutField("signature", "a"), new CheckoutField("signature", "b")),
                "key", HASH)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PaymentAttempt.prepare(payment(), 1, spec(), NOW.plusSeconds(600)))
                .isInstanceOf(IllegalStateException.class);
    }
}
