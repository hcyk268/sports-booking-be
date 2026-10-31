package dev.booking.sports.payment.api.dto.request;

import java.math.BigDecimal;
import java.util.UUID;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRefundRequest(
        @NotNull UUID paymentId,
        @NotNull UUID paymentTransactionId,
        @NotNull @DecimalMin("1") @Digits(integer = 13, fraction = 0) BigDecimal amount,
        @NotBlank @Size(max = 1000) String reason) {
}
