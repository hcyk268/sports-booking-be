package dev.booking.sports.identity.api.dto.request;

import java.util.Set;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ReplaceUserRolesRequest(
		@NotNull @PositiveOrZero Long version,
		@NotNull @NotEmpty Set<String> roleCodes) {
}
