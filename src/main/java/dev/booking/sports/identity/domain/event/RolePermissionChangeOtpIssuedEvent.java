package dev.booking.sports.identity.domain.event;

import java.time.Duration;
import java.util.UUID;

public record RolePermissionChangeOtpIssuedEvent(
		UUID userId,
		String email,
		String fullName,
		String roleCode,
		String otp,
		Duration ttl) {
}
