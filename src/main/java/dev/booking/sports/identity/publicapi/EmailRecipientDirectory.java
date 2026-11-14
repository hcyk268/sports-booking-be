package dev.booking.sports.identity.publicapi;

import java.util.Optional;
import java.util.UUID;

public interface EmailRecipientDirectory {
    Optional<EmailRecipient> findByUserId(UUID userId);
}
