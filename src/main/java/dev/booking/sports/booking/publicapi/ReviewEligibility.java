package dev.booking.sports.booking.publicapi;

import java.util.UUID;

public interface ReviewEligibility {
    boolean completedByCustomer(UUID bookingId, UUID customerId, UUID courtId);
}
