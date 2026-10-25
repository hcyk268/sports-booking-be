package dev.booking.sports.booking.application;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.booking.sports.booking.api.dto.response.BookingResponse;
import dev.booking.sports.booking.api.error.BookingErrorCode;
import dev.booking.sports.booking.domain.enums.BookingSlotStatus;
import dev.booking.sports.booking.domain.event.BookingHeldEvent;
import dev.booking.sports.booking.domain.event.BookingOutboxMessageTypes;
import dev.booking.sports.booking.domain.event.BookingSlotSnapshot;
import dev.booking.sports.booking.domain.model.Booking;
import dev.booking.sports.booking.domain.model.BookingSlot;
import dev.booking.sports.booking.domain.repository.BookingRepository;
import dev.booking.sports.booking.domain.repository.BookingSlotRepository;
import dev.booking.sports.court.publicapi.CourtSlotQuote;
import dev.booking.sports.shared.exception.ApiException;
import dev.booking.sports.shared.outbox.OutboxService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingCreationTransaction {

    private static final List<BookingSlotStatus> OCCUPYING_STATUSES = List.of(
            BookingSlotStatus.HELD,
            BookingSlotStatus.PENDING_PAYMENT,
            BookingSlotStatus.CONFIRMED);

    private final BookingRepository bookingRepository;
    private final BookingSlotRepository bookingSlotRepository;
    private final OutboxService outboxService;

    @Transactional
    public BookingResponse create(UUID customerId, String idempotencyKey, String requestHash,
                                  List<CourtSlotQuote> quotes, ZoneId zoneId, Instant now, Instant holdExpiresAt) {
        var existing = bookingRepository.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().hasIdempotencyRequestHash(requestHash)) {
                throw new ApiException(BookingErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
            return BookingResponse.from(existing.get());
        }

        List<BookingSlot.Spec> specs = quotes.stream().map(quote -> {
            Instant start = quote.date().atTime(quote.startTime()).atZone(zoneId).toInstant();
            Instant end = quote.date().atTime(quote.endTime()).atZone(zoneId).toInstant();
            if (!bookingSlotRepository.findOverlapping(
                    quote.courtId(), start, end, OCCUPYING_STATUSES).isEmpty()) {
                throw new ApiException(BookingErrorCode.BOOKING_SLOT_UNAVAILABLE);
            }
            return new BookingSlot.Spec(
                    quote.courtId(), start, end, quote.priceAmount());
        }).toList();

        Booking booking = Booking.hold(
                "BK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 26),
                customerId,
                quotes.getFirst().currency(),
                idempotencyKey,
                requestHash,
                holdExpiresAt,
                now,
                specs);
        bookingRepository.saveAndFlush(booking);

        List<BookingSlotSnapshot> slots = booking.getSlots().stream()
                .map(slot -> new BookingSlotSnapshot(
                        slot.getId(),
                        slot.getCourtId(),
                        slot.getStartAt(),
                        slot.getEndAt(),
                        slot.getPriceAmount()))
                .toList();

        outboxService.enqueue(
                BookingOutboxMessageTypes.BOOKING_AGGREGATE_TYPE,
                booking.getId(),
                BookingOutboxMessageTypes.BOOKING_HELD,
                BookingOutboxMessageTypes.EVENT_VERSION,
                new BookingHeldEvent(booking.getId(), booking.getBookingCode(),
                        customerId, booking.getTotalAmount(), booking.getCurrency(),
                        booking.getHoldExpiresAt(), now, slots));

        return BookingResponse.from(booking);
    }
}
