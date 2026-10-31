package dev.booking.sports.booking.application;

import java.time.Instant;
import java.util.UUID;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.booking.sports.booking.api.error.BookingErrorCode;
import dev.booking.sports.booking.domain.enums.BookingStatus;
import dev.booking.sports.booking.domain.model.Booking;
import dev.booking.sports.booking.domain.repository.BookingRepository;
import dev.booking.sports.booking.publicapi.BookingPaymentFacade;
import dev.booking.sports.booking.publicapi.BookingPaymentSnapshot;
import dev.booking.sports.shared.exception.ApiException;
import dev.booking.sports.court.publicapi.CourtFacade;
import lombok.RequiredArgsConstructor;
import dev.booking.sports.shared.outbox.OutboxService;
import dev.booking.sports.booking.domain.event.*;
import dev.booking.sports.booking.domain.enums.BookingSlotStatus;

@Service
@RequiredArgsConstructor
class BookingPaymentFacadeAdapter implements BookingPaymentFacade {
    private final BookingRepository bookingRepository;
    private final CourtFacade courtFacade;
    private final OutboxService outbox;

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Optional<BookingPaymentSnapshot> lockForPayment(UUID bookingId) {
        return bookingRepository.findByIdForUpdate(bookingId).map(this::snapshot);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public boolean confirmPayment(UUID bookingId, Instant now) {
        var booking = bookingRepository.findWithSlotsById(bookingId).orElseThrow();
        if (!snapshot(booking).awaitingPayment() || booking.getHoldExpiresAt() == null
                || !now.isBefore(booking.getHoldExpiresAt()) || booking.getSlots().isEmpty()
                || booking.getSlots().stream().anyMatch(slot -> slot.getStatus() != BookingSlotStatus.HELD
                    && slot.getStatus() != BookingSlotStatus.PENDING_PAYMENT)) {
            return false;
        }
        booking.confirm(now);
        outbox.enqueue(BookingOutboxMessageTypes.BOOKING_AGGREGATE_TYPE, bookingId,
                BookingOutboxMessageTypes.BOOKING_CONFIRMED, BookingOutboxMessageTypes.EVENT_VERSION,
                new BookingConfirmedEvent(bookingId, booking.getBookingCode(), booking.getCustomerId(),
                    booking.getTotalAmount(), booking.getCurrency(), now, booking.getSlots().stream()
                        .map(slot -> new BookingSlotSnapshot(slot.getId(), slot.getCourtId(), slot.getStartAt(),
                            slot.getEndAt(), slot.getPriceAmount())).toList()));
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public BookingPaymentSnapshot getAccessibleBooking(UUID actorId, Set<String> authorities, UUID bookingId) {
        var booking = bookingRepository.findWithSlotsById(bookingId)
                .orElseThrow(() -> new ApiException(BookingErrorCode.BOOKING_NOT_FOUND));
        var roles = Arrays.stream(BookingActorRole.values())
                .filter(role -> authorities.contains("ROLE_" + role.name())).collect(Collectors.toSet());
        if (!BookingAccessPolicy.canManage(new BookingViewer(actorId, roles), booking, courtFacade)) {
            throw new ApiException(BookingErrorCode.BOOKING_NOT_FOUND);
        }
        return snapshot(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, BookingPaymentSnapshot> getAccessibleBookings(UUID actorId, Set<String> authorities,
            Collection<UUID> bookingIds) {
        if (bookingIds.isEmpty()) {
            return Map.of();
        }
        var ids = Set.copyOf(bookingIds);
        var bookings = bookingRepository.findWithSlotsByIdIn(ids);
        if (bookings.size() != ids.size()) {
            throw new ApiException(BookingErrorCode.BOOKING_NOT_FOUND);
        }
        boolean admin = authorities.contains("ROLE_ADMIN");
        boolean customer = authorities.contains("ROLE_CUSTOMER");
        boolean provider = authorities.contains("ROLE_PROVIDER");
        Set<UUID> ownedCourtIds = !admin && provider ? courtFacade.findOwnedCourtIds(actorId) : Set.of();
        for (var booking : bookings) {
            boolean ownsBooking = customer && actorId.equals(booking.getCustomerId());
            boolean ownsCourts = provider && !booking.getSlots().isEmpty()
                    && booking.getSlots().stream().allMatch(slot -> ownedCourtIds.contains(slot.getCourtId()));
            if (!admin && !ownsBooking && !ownsCourts) {
                throw new ApiException(BookingErrorCode.BOOKING_NOT_FOUND);
            }
        }
        return bookings.stream().collect(Collectors.toMap(Booking::getId, this::snapshot));
    }

    private BookingPaymentSnapshot snapshot(Booking booking) {
        return new BookingPaymentSnapshot(booking.getId(), booking.getBookingCode(), booking.getCustomerId(),
                booking.getTotalAmount(), booking.getCurrency(), booking.getHoldExpiresAt(),
                booking.getStatus() == BookingStatus.HELD || booking.getStatus() == BookingStatus.PENDING_PAYMENT);
    }

    @Transactional
    @Override
    public BookingPaymentSnapshot lockOwnedBooking(UUID customerId, UUID bookingId) {
        var booking = bookingRepository.findOwnedByIdForUpdate(bookingId, customerId)
                .orElseThrow(() -> new ApiException(BookingErrorCode.BOOKING_NOT_FOUND));
        boolean awaitingPayment = booking.getStatus() == BookingStatus.HELD
                || booking.getStatus() == BookingStatus.PENDING_PAYMENT;
        return new BookingPaymentSnapshot(booking.getId(), booking.getBookingCode(), booking.getCustomerId(),
                booking.getTotalAmount(), booking.getCurrency(), booking.getHoldExpiresAt(), awaitingPayment);
    }

    @Transactional
    @Override
    public void startPayment(UUID customerId, UUID bookingId, Instant now) {
        var booking = bookingRepository.findOwnedByIdForUpdate(bookingId, customerId)
                .orElseThrow(() -> new ApiException(BookingErrorCode.BOOKING_NOT_FOUND));
        if ((booking.getStatus() != BookingStatus.HELD && booking.getStatus() != BookingStatus.PENDING_PAYMENT)
                || booking.getHoldExpiresAt() == null || !now.isBefore(booking.getHoldExpiresAt())) {
            throw new ApiException(BookingErrorCode.BOOKING_PAYMENT_NOT_ALLOWED);
        }
        if (booking.getStatus() == BookingStatus.HELD) {
            booking.startPayment(now);
        }
    }
}
