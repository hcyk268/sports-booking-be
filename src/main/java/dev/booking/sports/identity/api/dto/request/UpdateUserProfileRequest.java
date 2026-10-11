package dev.booking.sports.identity.api.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(
		@Size(max = 100) String fullName,

		@Size(max = 15) @Pattern(regexp = "^[0-9+\\-\\s]*$", message = "must contain digits only") String phone) {
}
