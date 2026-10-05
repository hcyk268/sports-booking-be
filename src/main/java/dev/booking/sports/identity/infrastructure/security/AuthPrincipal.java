package dev.booking.sports.identity.infrastructure.security;

import java.util.Set;
import java.util.UUID;

public record AuthPrincipal(
		UUID userId,
		String email,
		String jti,
		Set<String> roles,
		Set<String> permissions) {
}
