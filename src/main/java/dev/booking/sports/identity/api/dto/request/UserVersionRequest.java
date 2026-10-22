package dev.booking.sports.identity.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UserVersionRequest(
		@NotNull @PositiveOrZero Long version) {
}
