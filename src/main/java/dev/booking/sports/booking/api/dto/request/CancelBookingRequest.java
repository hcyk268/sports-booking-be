package dev.booking.sports.booking.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CancelBookingRequest(
        @NotNull @PositiveOrZero Long version,
        @NotBlank @Size(max = 500) String reason) {
}
