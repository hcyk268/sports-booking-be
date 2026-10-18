package dev.booking.sports.shared.upload;

import java.util.Set;
import java.util.UUID;

/** Implemented by the consuming module; shared never imports that module. */
public interface UploadPolicy {
    String purpose();
    void authorize(UUID actorId, UUID resourceId);
    default void checkCapacity(UUID resourceId) {}
    long maxBytes();
    Set<String> contentTypes();
}
