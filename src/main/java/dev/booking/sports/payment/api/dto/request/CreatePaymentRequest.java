package dev.booking.sports.payment.api.dto.request;

import jakarta.validation.constraints.Pattern;

public record CreatePaymentRequest(
        @Pattern(regexp = "BANK_TRANSFER|NAPAS_BANK_TRANSFER") String paymentMethod) {
}
