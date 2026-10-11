package dev.booking.sports.identity.api.dto.response;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import dev.booking.sports.identity.domain.model.Role;

public record RoleResponse(
		UUID id,
		String code,
		String name,
		String description,
		Set<String> permissionCodes) {

	public static RoleResponse from(Role role) {
		Set<String> permissionCodes = role.getRolePermissions().stream()
				.map(rolePermission -> rolePermission.getPermission().getCode())
				.collect(Collectors.toCollection(TreeSet::new));
		return new RoleResponse(
				role.getId(),
				role.getCode(),
				role.getName(),
				role.getDescription(),
				Set.copyOf(permissionCodes));
	}
}
