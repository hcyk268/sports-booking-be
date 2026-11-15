package dev.booking.sports.booking.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.booking.sports.booking.api.dto.response.BookingResponse;
import dev.booking.sports.booking.api.error.BookingErrorCode;
import dev.booking.sports.booking.domain.enums.BookingStatus;
import dev.booking.sports.booking.domain.repository.BookingRepository;
import dev.booking.sports.court.publicapi.CourtFacade;
import dev.booking.sports.shared.exception.ApiException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingCompletionService {
    private final BookingRepository bookings;
    private final CourtFacade courts;

    @Transactional
    public BookingResponse complete(UUID id, BookingViewer viewer) {
        var booking = bookings.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiException(BookingErrorCode.BOOKING_NOT_FOUND));
        var slots = booking.getSlots();
        if (!viewer.hasRole(BookingActorRole.ADMIN) && !(viewer.hasRole(BookingActorRole.PROVIDER)
                && courts.ownsAllCourts(viewer.userId(), slots.stream().map(slot -> slot.getCourtId()).distinct().toList()))) {
            throw new ApiException(BookingErrorCode.BOOKING_NOT_FOUND);
        }
        if (booking.getStatus() == BookingStatus.COMPLETED) return BookingResponse.from(booking);
        Instant now = Instant.now();
        if (booking.getStatus() != BookingStatus.CONFIRMED || slots.isEmpty()
                || slots.stream().anyMatch(slot -> slot.getEndAt().isAfter(now))) {
            throw new ApiException(BookingErrorCode.BOOKING_COMPLETION_NOT_ALLOWED);
        }
        booking.complete(now);
        bookings.flush();
        return BookingResponse.from(booking);
    }
}
