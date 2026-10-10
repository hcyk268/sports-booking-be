package dev.booking.sports.identity.api.dto.request;

import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record ApplyRolePermissionsRequest(
		@NotNull @NotEmpty Set<String> permissionCodes,

		@NotBlank String otp) {
}
