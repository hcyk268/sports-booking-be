package dev.booking.sports.identity.publicapi;

import java.time.Instant;
import java.util.UUID;

/** Immutable access-token metadata; never retains the bearer token itself. */
public record StreamSession(UUID userId, String jti, long tokenVersion, Instant expiresAt) {}
