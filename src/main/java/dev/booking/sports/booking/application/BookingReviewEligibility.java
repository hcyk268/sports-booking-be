package dev.booking.sports.booking.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import dev.booking.sports.booking.domain.enums.BookingStatus;
import dev.booking.sports.booking.domain.repository.BookingRepository;
import dev.booking.sports.booking.publicapi.ReviewEligibility;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class BookingReviewEligibility implements ReviewEligibility {
    private final BookingRepository bookings;

    public boolean completedByCustomer(UUID bookingId, UUID customerId, UUID courtId) {
        return bookings.findByIdAndCustomerId(bookingId, customerId)
                .filter(booking -> booking.getStatus() == BookingStatus.COMPLETED)
                .stream().flatMap(booking -> booking.getSlots().stream())
                .anyMatch(slot -> slot.getCourtId().equals(courtId));
    }
}
