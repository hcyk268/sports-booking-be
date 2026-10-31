package dev.booking.sports.payment.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import dev.booking.sports.payment.domain.enums.PaymentStatus;

public record PaymentCheckoutResponse(
        UUID paymentId,
        UUID attemptId,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        Instant expiresAt,
        CheckoutForm checkout) {

    public record CheckoutForm(String action, String method, List<Field> fields) {
        @Override
        public String toString() {
            return "CheckoutForm[action=" + action + ", method=" + method + ", fields=<redacted>]";
        }
    }

    public record Field(String name, String value) {
        @Override
        public String toString() {
            return "Field[name=" + name + ", value=<redacted>]";
        }
    }
}
