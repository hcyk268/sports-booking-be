package dev.booking.sports.identity.domain.event;

import java.util.UUID;

public record EmailVerificationRequestedEvent(UUID userId, String email, String fullName, String verificationUrl) {
}
