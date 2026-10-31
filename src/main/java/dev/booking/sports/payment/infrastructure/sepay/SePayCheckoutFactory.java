package dev.booking.sports.payment.infrastructure.sepay;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import dev.booking.sports.booking.publicapi.BookingPaymentSnapshot;
import dev.booking.sports.payment.config.SePayProperties;
import dev.booking.sports.payment.api.error.PaymentErrorCode;
import dev.booking.sports.payment.domain.enums.PaymentEnvironment;
import dev.booking.sports.shared.exception.ApiException;
import dev.booking.sports.payment.domain.model.Payment;
import dev.booking.sports.payment.domain.model.PaymentAttempt.CheckoutField;
import dev.booking.sports.payment.domain.model.PaymentAttempt.CheckoutSpec;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SePayCheckoutFactory {

    private final SePayProperties properties;

    public CheckoutSpec create(Payment payment, BookingPaymentSnapshot booking, UUID customerId,
            String paymentMethod, String idempotencyKey, String requestHash) {
        requireEnabled();
        UUID paymentId = Objects.requireNonNull(payment.getId(), "Payment must be persisted before signing checkout");
        String invoice = "PAY" + UUID.randomUUID().toString().replace("-", "");
        List<CheckoutField> fields = new ArrayList<>();
        // Preserve SePay's documented signing order and unencoded field values.
        fields.add(new CheckoutField("order_amount", payment.getAmount().toBigIntegerExact().toString()));
        fields.add(new CheckoutField("merchant", properties.merchantId()));
        fields.add(new CheckoutField("currency", payment.getCurrency()));
        fields.add(new CheckoutField("operation", "PURCHASE"));
        fields.add(new CheckoutField("order_description", "Thanh toan dat san " + booking.bookingCode()));
        fields.add(new CheckoutField("order_invoice_number", invoice));
        fields.add(new CheckoutField("customer_id", customerId.toString()));
        if (paymentMethod != null) {
            fields.add(new CheckoutField("payment_method", paymentMethod));
        }
        fields.add(new CheckoutField("success_url", callback(properties.successUrl(), paymentId)));
        fields.add(new CheckoutField("error_url", callback(properties.errorUrl(), paymentId)));
        fields.add(new CheckoutField("cancel_url", callback(properties.cancelUrl(), paymentId)));
        fields.add(new CheckoutField("signature", sign(fields)));
        return new CheckoutSpec(properties.environment(), properties.merchantId(), invoice,
                paymentMethod, fields, idempotencyKey, requestHash);
    }

    public static String checkoutAction(PaymentEnvironment environment) {
        return environment == PaymentEnvironment.PRODUCTION
                ? "https://pay.sepay.vn/v1/checkout/init"
                : "https://pay-sandbox.sepay.vn/v1/checkout/init";
    }

    void requireEnabled() {
        if (!properties.enabled()) {
            throw new ApiException(PaymentErrorCode.SEPAY_GATEWAY_NOT_CONFIGURED);
        }
    }

    private static String callback(String url, UUID paymentId) {
        return UriComponentsBuilder.fromUriString(url).replaceQueryParam("paymentId", paymentId)
                .build(true).toUriString();
    }

    String sign(List<CheckoutField> fields) {
        String data = fields.stream().map(field -> field.name() + "=" + field.value())
                .collect(Collectors.joining(","));
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.secretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot sign SePay checkout", exception);
        }
    }
}
