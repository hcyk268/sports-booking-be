package dev.booking.sports.booking.application;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record BookingViewer(
        UUID userId,
        Set<BookingActorRole> roles) {

    public BookingViewer {
        Objects.requireNonNull(userId, "userId must not be null");
        roles = Set.copyOf(Objects.requireNonNull(
                roles,
                "roles must not be null"));
    }

    public boolean hasRole(BookingActorRole role) {
        return roles.contains(role);
    }
}
