package dev.booking.sports.identity.api.dto.response;

import java.util.Set;
import java.util.UUID;

import dev.booking.sports.identity.domain.enums.UserStatus;
import dev.booking.sports.identity.domain.model.User;

public record UserSummaryResponse(
		UUID id,
		String email,
		String fullName,
		String phone,
		UserStatus status,
		Set<String> roles,
		Set<String> permissions) {

	public static UserSummaryResponse from(User user) {
		return new UserSummaryResponse(
				user.getId(),
				user.getEmail(),
				user.getFullName(),
				user.getPhone(),
				user.getStatus(),
				user.roleCodes(),
				user.permissionCodes());
	}
}
