package dev.booking.sports.identity.api.dto.request;

import java.util.Set;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
		@NotBlank @Email @Size(max = 255) String email,
		@NotBlank @Size(max = 100) String fullName,
		@Size(max = 15) @Pattern(regexp = "^[0-9+\\-\\s]*$", message = "must contain digits only") String phone,
		@NotNull @NotEmpty Set<String> roleCodes) {
}
