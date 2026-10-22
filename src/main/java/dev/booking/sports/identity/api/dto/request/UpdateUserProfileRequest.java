package dev.booking.sports.identity.api.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(
		@NotNull @PositiveOrZero Long version,

		@Size(max = 100) String fullName,

		@Size(max = 15) @Pattern(regexp = "^[0-9+\\-\\s]*$", message = "must contain digits only") String phone) {
}
