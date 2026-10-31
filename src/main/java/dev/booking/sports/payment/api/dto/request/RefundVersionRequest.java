package dev.booking.sports.payment.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record RefundVersionRequest(@NotNull @PositiveOrZero Long version) {
}
