package dev.booking.sports.payment.domain.model;

import static dev.booking.sports.payment.domain.model.PaymentFixtures.*;
import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import dev.booking.sports.payment.domain.enums.PaymentRefundStatus;

class PaymentRefundTest {
    @Test
    void manualRefundRecordsActorReferenceAndAuditWithoutClearingSettlement() {
        Payment payment = payment();
        PaymentTransaction receipt = receipt(attempt(payment), "300000");
        payment.settle(receipt, NOW.plusSeconds(60));
        PaymentRefund refund = refund(receipt, AMOUNT);
        UUID admin = UUID.randomUUID();
        UUID evidence = UUID.randomUUID();
        refund.startProcessing(admin, NOW.plusSeconds(70));
        refund.complete(admin, "BANK-REF-123", evidence, "Bank transfer completed", NOW.plusSeconds(90));
        assertThat(refund.getStatus()).isEqualTo(PaymentRefundStatus.COMPLETED);
        assertThat(refund.reservesFunds()).isTrue();
        assertThat(refund.getProcessedBy()).isEqualTo(admin);
        assertThat(refund.getEvidenceAssetId()).isEqualTo(evidence);
        assertThat(refund.getStatusHistory()).hasSize(3);
        assertThat(payment.getSettledTransactionId()).isEqualTo(receipt.getId());
        assertThatThrownBy(() -> refund.complete(admin, "OTHER", null, null, NOW.plusSeconds(100)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesOversizedUnmatchedAndReversedRefunds() {
        PaymentTransaction receipt = receipt(attempt(payment()), "300000");
        assertThatThrownBy(() -> refund(receipt, new BigDecimal("300001"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> refund(receipt(null, "300000"), AMOUNT)).isInstanceOf(IllegalStateException.class);
        receipt.recordProviderStatus("VOIDED");
        assertThatThrownBy(() -> refund(receipt, AMOUNT)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void processingCannotBeCancelledOrRejectedWhileExternalOutcomeMayBeUnknown() {
        PaymentRefund refund = refund(receipt(attempt(payment()), "300000"), AMOUNT);
        UUID admin = UUID.randomUUID();
        refund.startProcessing(admin, NOW);
        assertThatThrownBy(() -> refund.cancel(admin, "Cancel")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> refund.reject(admin, "Reject")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> refund.complete(admin, "REF", null, null, NOW.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingCompletionReferenceLeavesRefundUnchanged() {
        PaymentRefund refund = refund(receipt(attempt(payment()), "300000"), AMOUNT);
        UUID admin = UUID.randomUUID();
        refund.startProcessing(admin, NOW);
        assertThatThrownBy(() -> refund.complete(admin, " ", null, null, NOW.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(refund.getStatus()).isEqualTo(PaymentRefundStatus.PROCESSING);
        assertThat(refund.getCompletedAt()).isNull();
    }

    private PaymentRefund refund(PaymentTransaction receipt, BigDecimal amount) {
        return PaymentRefund.request(receipt, amount, "Booking cancellation", "refund-key", HASH, null);
    }
}
