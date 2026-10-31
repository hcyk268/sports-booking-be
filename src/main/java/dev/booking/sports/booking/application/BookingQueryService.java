package dev.booking.sports.booking.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.booking.sports.booking.api.dto.response.BookingResponse;
import dev.booking.sports.booking.api.error.BookingErrorCode;
import dev.booking.sports.booking.config.BookingProperties;
import dev.booking.sports.booking.domain.enums.BookingStatus;
import dev.booking.sports.booking.domain.model.Booking;
import dev.booking.sports.booking.domain.repository.BookingRepository;
import dev.booking.sports.court.publicapi.CourtFacade;
import dev.booking.sports.shared.api.PageResponse;
import dev.booking.sports.shared.exception.ApiException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingQueryService {

    private final BookingRepository bookingRepository;
    private final BookingProperties bookingProperties;
    private final CourtFacade courtFacade;

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> listMyBookings(
            UUID customerId,
            BookingStatus status,
            LocalDate date,
            int page,
            int size) {
        validatePagination(page, size);

        Instant rangeStart = date == null ? null : date.atStartOfDay(bookingProperties.zoneId()).toInstant();
        Instant rangeEnd = date == null ? null : date.plusDays(1)
                                                    .atStartOfDay(bookingProperties.zoneId())
                                                    .toInstant();

        PageRequest pageable = PageRequest.of(page, size);

        Page<Booking> result = bookingRepository.findCustomerBookings(
                customerId,
                status,
                rangeStart,
                rangeEnd,
                pageable);

        List<BookingResponse> items = loadResponses(result.getContent());
        return PageResponse.of(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> listBookings(
            BookingViewer viewer,
            UUID customerId,
            UUID courtId,
            BookingStatus status,
            LocalDate date,
            int page,
            int size) {
        validatePagination(page, size);

        Instant rangeStart = date == null ? null : date.atStartOfDay(bookingProperties.zoneId()).toInstant();
        Instant rangeEnd = date == null ? null : date.plusDays(1).atStartOfDay(bookingProperties.zoneId()).toInstant();

        PageRequest pageable = PageRequest.of(page, size);

        Page<Booking> result;
        if (viewer.hasRole(BookingActorRole.ADMIN)) {
            result = bookingRepository.findBookings(
                    customerId,
                    courtId,
                    status,
                    rangeStart,
                    rangeEnd,
                    pageable);
        } else if (viewer.hasRole(BookingActorRole.PROVIDER)) {
            Set<UUID> ownedCourtIds = courtFacade.findOwnedCourtIds(viewer.userId());
            if (ownedCourtIds.isEmpty()
                    || (courtId != null && !ownedCourtIds.contains(courtId))) {
                return PageResponse.of(Collections.emptyList(), page, size, 0);
            }
            result = bookingRepository.findProviderBookings(
                    ownedCourtIds,
                    customerId,
                    courtId,
                    status,
                    rangeStart,
                    rangeEnd,
                    pageable);
        } else {
            throw new ApiException(BookingErrorCode.BOOKING_ACCESS_DENIED);
        }

        return PageResponse.of(
                loadResponses(result.getContent()),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public BookingResponse getBooking(
            BookingViewer viewer,
            UUID bookingId) {
        Booking booking = bookingRepository
                .findWithSlotsById(bookingId)
                .orElseThrow(() -> new ApiException(BookingErrorCode.BOOKING_NOT_FOUND));

        if (!BookingAccessPolicy.canManage(viewer, booking, courtFacade)) {
            throw new ApiException(BookingErrorCode.BOOKING_NOT_FOUND);
        }

        return BookingResponse.from(booking);
    }

    private List<BookingResponse> loadResponses(List<Booking> bookings) {
        if (bookings.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> bookingIds = bookings.stream()
                .map(Booking::getId)
                .toList();
        Map<UUID, Booking> bookingsWithSlots = bookingRepository
                .findWithSlotsByIdIn(bookingIds)
                .stream()
                .collect(Collectors.toMap(
                        Booking::getId,
                        Function.identity()));

        return bookingIds.stream()
                .map(bookingsWithSlots::get)
                .map(BookingResponse::from)
                .toList();
    }

    private void validatePagination(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(
                    BookingErrorCode.INVALID_BOOKING_PAGINATION);
        }
    }
}
