package dev.booking.sports.court.publicapi;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface CourtFacade {

    Set<UUID> findOwnedCourtIds(UUID providerId);

    Optional<List<CourtSlotQuote>> quoteBookableSlots(
            UUID courtId,
            List<CourtSlotSelection> selections);

    boolean ownsAllCourts(
            UUID providerId,
            Collection<UUID> courtIds);
}
