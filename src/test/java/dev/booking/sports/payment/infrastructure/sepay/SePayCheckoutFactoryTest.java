package dev.booking.sports.payment.infrastructure.sepay;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import dev.booking.sports.booking.publicapi.BookingPaymentSnapshot;
import dev.booking.sports.payment.api.error.PaymentErrorCode;
import dev.booking.sports.payment.config.SePayProperties;
import dev.booking.sports.payment.domain.enums.PaymentEnvironment;
import dev.booking.sports.payment.domain.model.Payment;
import dev.booking.sports.payment.domain.model.PaymentAttempt.CheckoutField;
import dev.booking.sports.shared.exception.ApiException;

class SePayCheckoutFactoryTest {
    private static final String SUCCESS = "https://yoursite.com/payment/success";
    private static final String ERROR = "https://yoursite.com/payment/error";
    private static final String CANCEL = "https://yoursite.com/payment/cancel";

    @Test
    void signsDocumentedUnicodeExampleWithIndependentKnownHmacVector() {
        var factory = new SePayCheckoutFactory(properties());
        var fields = List.of(new CheckoutField("order_amount", "100000"),
                new CheckoutField("merchant", "MERCHANT_123"), new CheckoutField("currency", "VND"),
                new CheckoutField("operation", "PURCHASE"),
                new CheckoutField("order_description", "Thanh toán đơn hàng #12345"),
                new CheckoutField("order_invoice_number", "INV_20231201_001"),
                new CheckoutField("success_url", SUCCESS),
                new CheckoutField("error_url", ERROR),
                new CheckoutField("cancel_url", CANCEL));
        // Reference value independently calculated with .NET HMACSHA256/UTF8/Base64.
        assertThat(factory.sign(fields)).isEqualTo("rLDrODxYO5yUmBhRcQZ0R9PESqfFYlXjOn/NhajG5HM=");
    }

    @Test
    void createsOrderedSignedFormFromServerSnapshotWithoutInventingGatewayExpiry() {
        Instant now = Instant.parse("2030-01-01T00:00:00Z");
        UUID customer = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Payment payment = Payment.pending(bookingId, new BigDecimal("300000.00"), "VND", now.plusSeconds(600), now);
        payment.setId(UUID.randomUUID());
        var booking = new BookingPaymentSnapshot(bookingId, "BK001", customer,
                payment.getAmount(), "VND", payment.getExpiresAt(), true);
        var factory = new SePayCheckoutFactory(properties());
        var spec = factory.create(payment, booking, customer, "BANK_TRANSFER", "key", "a".repeat(64));
        assertThat(spec.fields()).extracting(CheckoutField::name).containsExactly("order_amount", "merchant",
                "currency", "operation", "order_description", "order_invoice_number", "customer_id",
                "payment_method", "success_url", "error_url", "cancel_url", "signature");
        assertThat(spec.fields().getFirst().value()).isEqualTo("300000");
        assertThat(spec.fields().stream().filter(field -> field.name().equals("success_url"))
                .findFirst().orElseThrow().value()).isEqualTo(SUCCESS + "?paymentId=" + payment.getId());
        assertThat(spec.invoiceNumber()).startsWith("PAY");
        assertThat(spec.fields().stream().filter(field -> field.name().equals("order_invoice_number"))
                .findFirst().orElseThrow().value()).isEqualTo(spec.invoiceNumber());
        assertThat(spec.fields().toString()).doesNotContain("test-secret");
        var selectedAtGateway = factory.create(payment, booking, customer, null, "other", "b".repeat(64));
        assertThat(selectedAtGateway.fields()).extracting(CheckoutField::name).doesNotContain("payment_method");
        assertThat(selectedAtGateway.invoiceNumber()).isNotEqualTo(spec.invoiceNumber());
    }

    @Test
    void disabledGatewayFailsWithoutLeakingSecret() {
        var properties = new SePayProperties(false, null, "merchant", "private-secret", SUCCESS, ERROR, CANCEL);
        assertThatThrownBy(new SePayCheckoutFactory(properties)::requireEnabled).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(PaymentErrorCode.SEPAY_GATEWAY_NOT_CONFIGURED));
        assertThat(properties.toString()).doesNotContain("private-secret");
        assertThat(SePayCheckoutFactory.checkoutAction(PaymentEnvironment.PRODUCTION))
                .isEqualTo("https://pay.sepay.vn/v1/checkout/init");
        assertThat(SePayCheckoutFactory.checkoutAction(properties().environment()))
                .isEqualTo("https://pay-sandbox.sepay.vn/v1/checkout/init");
    }

    private SePayProperties properties() {
        return new SePayProperties(true, PaymentEnvironment.SANDBOX, "MERCHANT_123", "test-secret", SUCCESS, ERROR, CANCEL);
    }
}
