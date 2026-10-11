package dev.booking.sports.identity.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePasswordRequest(

		@NotBlank @Pattern(regexp = "^\\d{6}$", message = "must be 6 digits") String otp,

		@NotBlank @Pattern(
				regexp = PasswordRules.PATTERN,
				message = PasswordRules.MESSAGE) String newPassword) {
}
