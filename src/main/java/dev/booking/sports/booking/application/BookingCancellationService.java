package dev.booking.sports.booking.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.booking.sports.booking.api.dto.request.CancelBookingRequest;
import dev.booking.sports.booking.api.dto.response.BookingResponse;
import dev.booking.sports.booking.api.error.BookingErrorCode;
import dev.booking.sports.booking.domain.event.BookingCancelledEvent;
import dev.booking.sports.booking.domain.event.BookingOutboxMessageTypes;
import dev.booking.sports.booking.domain.event.BookingSlotSnapshot;
import dev.booking.sports.booking.domain.repository.BookingRepository;
import dev.booking.sports.court.publicapi.CourtFacade;
import dev.booking.sports.shared.exception.ApiException;
import dev.booking.sports.shared.exception.ConcurrencyErrorCode;
import dev.booking.sports.shared.outbox.OutboxService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingCancellationService {

    private final BookingRepository bookingRepository;
    private final CourtFacade courtFacade;
    private final OutboxService outboxService;

    @Transactional
    public BookingResponse cancelBooking(
            BookingViewer viewer,
            UUID bookingId,
            CancelBookingRequest request) {
        var booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ApiException(BookingErrorCode.BOOKING_NOT_FOUND));

        if (!BookingAccessPolicy.canManage(viewer, booking, courtFacade)) {
            throw new ApiException(BookingErrorCode.BOOKING_NOT_FOUND);
        }
        if (!booking.getVersion().equals(request.version())) {
            throw new ApiException(ConcurrencyErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
        if (booking.getStatus().isTerminal()) {
            throw new ApiException(BookingErrorCode.BOOKING_CANCELLATION_NOT_ALLOWED);
        }

        Instant now = Instant.now();
        booking.cancel(viewer.userId(), request.reason(), now);
        bookingRepository.flush();

        outboxService.enqueue(
                BookingOutboxMessageTypes.BOOKING_AGGREGATE_TYPE,
                booking.getId(),
                BookingOutboxMessageTypes.BOOKING_CANCELLED,
                BookingOutboxMessageTypes.EVENT_VERSION,
                new BookingCancelledEvent(
                        booking.getId(),
                        booking.getBookingCode(),
                        booking.getCustomerId(),
                        booking.getTotalAmount(),
                        booking.getCurrency(),
                        viewer.userId(),
                        request.reason(),
                        now,
                        booking.getSlots().stream()
                                .map(slot -> new BookingSlotSnapshot(
                                        slot.getId(),
                                        slot.getCourtId(),
                                        slot.getStartAt(),
                                        slot.getEndAt(),
                                        slot.getPriceAmount()))
                                .toList()));
        return BookingResponse.from(booking);
    }
}
