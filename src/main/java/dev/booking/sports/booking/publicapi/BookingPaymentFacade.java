package dev.booking.sports.booking.publicapi;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.Optional;

public interface BookingPaymentFacade {
    /** Read access using authority names from the authenticated user, without row locks. */
    BookingPaymentSnapshot getAccessibleBooking(UUID actorId, Set<String> authorities, UUID bookingId);

    /** Reads a batch with the same access policy, without a query for each booking. */
    Map<UUID, BookingPaymentSnapshot> getAccessibleBookings(UUID actorId, Set<String> authorities,
            Collection<UUID> bookingIds);

    /** Caller must already have a transaction; the booking row stays locked until commit. */
    BookingPaymentSnapshot lockOwnedBooking(UUID customerId, UUID bookingId);

    /** Starts payment or leaves an already pending booking unchanged, preserving its deadline. */
    void startPayment(UUID customerId, UUID bookingId, Instant now);

    /** Internal settlement operations; caller owns the payment processing transaction. */
    Optional<BookingPaymentSnapshot> lockForPayment(UUID bookingId);

    boolean confirmPayment(UUID bookingId, Instant now);
}
