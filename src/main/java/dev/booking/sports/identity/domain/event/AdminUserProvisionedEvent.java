package dev.booking.sports.identity.domain.event;

import java.util.UUID;

public record AdminUserProvisionedEvent(
		UUID userId,
		String email,
		String fullName,
		String temporaryPassword,
		String loginUrl) {
}
