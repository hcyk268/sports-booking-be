package dev.booking.sports.identity.domain.event;

import java.time.Duration;
import java.util.UUID;

public record PasswordChangeOtpIssuedEvent(UUID userId, String email, String fullName, String otp, Duration ttl) {
}
