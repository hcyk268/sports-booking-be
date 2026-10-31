package dev.booking.sports.booking.application;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.booking.sports.booking.domain.event.BookingExpiredEvent;
import dev.booking.sports.booking.domain.event.BookingOutboxMessageTypes;
import dev.booking.sports.booking.domain.event.BookingSlotSnapshot;
import dev.booking.sports.booking.domain.repository.BookingRepository;
import dev.booking.sports.shared.outbox.OutboxService;
import dev.booking.sports.shared.config.PaymentSettlementProperties;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingExpiryService {

    private final BookingRepository bookingRepository;
    private final OutboxService outboxService;
    private final PaymentSettlementProperties settlementProperties;

    @Transactional
    public int expireBatch(int limit) {
        Instant now = Instant.now();
        var ids = bookingRepository.lockExpiredHoldIds(limit, now, now.minus(settlementProperties.grace()));
        for (var id : ids) {
            var booking = bookingRepository.findWithSlotsById(id).orElseThrow();
            booking.expire(now);
            outboxService.enqueue(
                    BookingOutboxMessageTypes.BOOKING_AGGREGATE_TYPE,
                    booking.getId(),
                    BookingOutboxMessageTypes.BOOKING_EXPIRED,
                    BookingOutboxMessageTypes.EVENT_VERSION,
                    new BookingExpiredEvent(
                            booking.getId(),
                            booking.getBookingCode(),
                            booking.getCustomerId(),
                            booking.getTotalAmount(),
                            booking.getCurrency(),
                            now,
                            booking.getSlots().stream()
                                    .map(slot -> new BookingSlotSnapshot(
                                            slot.getId(),
                                            slot.getCourtId(),
                                            slot.getStartAt(),
                                            slot.getEndAt(),
                                            slot.getPriceAmount()))
                                    .toList()));
        }
        return ids.size();
    }
}
