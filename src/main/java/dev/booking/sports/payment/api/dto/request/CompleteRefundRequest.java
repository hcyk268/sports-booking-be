package dev.booking.sports.payment.api.dto.request;

import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CompleteRefundRequest(
        @NotNull @PositiveOrZero Long version,
        @NotBlank @Size(max = 255) String refundReference,
        UUID evidenceAssetId,
        @Size(max = 2000) String adminNote) {
}
