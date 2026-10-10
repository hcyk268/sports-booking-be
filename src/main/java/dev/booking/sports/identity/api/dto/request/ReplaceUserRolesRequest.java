package dev.booking.sports.identity.api.dto.request;

import java.util.Set;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record ReplaceUserRolesRequest(@NotNull @NotEmpty Set<String> roleCodes) {
}
