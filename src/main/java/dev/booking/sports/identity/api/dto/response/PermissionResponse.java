package dev.booking.sports.identity.api.dto.response;

import java.util.UUID;

import dev.booking.sports.identity.domain.model.Permission;

public record PermissionResponse(
		UUID id,
		String code,
		String name,
		String description,
		String module) {

	public static PermissionResponse from(Permission permission) {
		return new PermissionResponse(
				permission.getId(),
				permission.getCode(),
				permission.getName(),
				permission.getDescription(),
				permission.getModule());
	}
}
