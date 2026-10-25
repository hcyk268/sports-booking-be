package dev.booking.sports.booking.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.booking.sports.booking.domain.enums.BookingSlotStatus;
import dev.booking.sports.booking.domain.enums.BookingStatus;

import org.junit.jupiter.api.Test;

class BookingTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID COURT_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    private static final String REQUEST_HASH = "a".repeat(64);

    @Test
    void createsHoldForMultipleNonContiguousSlots() {
        Booking booking = bookingWithSlots(List.of(
                slot("2030-01-02T01:00:00Z", "2030-01-02T03:00:00Z", "100000"),
                slot("2030-01-02T03:00:00Z", "2030-01-02T05:00:00Z", "150000"),
                slot("2030-01-02T11:00:00Z", "2030-01-02T13:00:00Z", "200000")));

        assertEquals(BookingStatus.HELD, booking.getStatus());
        assertEquals(new BigDecimal("450000"), booking.getTotalAmount());
        assertEquals(3, booking.getSlots().size());
        assertTrue(booking.getSlots().stream()
                .allMatch(slot -> slot.getStatus() == BookingSlotStatus.HELD));
        assertEquals(1, booking.getStatusHistory().size());
    }

    @Test
    void rejectsOverlappingSlotsForSameCourt() {
        List<BookingSlot.Spec> slots = List.of(
                slot("2030-01-02T01:00:00Z", "2030-01-02T03:00:00Z", "100000"),
                slot("2030-01-02T02:00:00Z", "2030-01-02T04:00:00Z", "100000"));

        assertThrows(IllegalArgumentException.class, () -> bookingWithSlots(slots));
    }

    @Test
    void confirmingBookingUpdatesEverySlotAndHistory() {
        Booking booking = bookingWithSlots(List.of(
                slot("2030-01-02T01:00:00Z", "2030-01-02T03:00:00Z", "100000")));
        booking.startPayment(NOW.plusSeconds(60));

        booking.confirm(NOW.plusSeconds(120));

        assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        assertEquals(BookingSlotStatus.CONFIRMED, booking.getSlots().getFirst().getStatus());
        assertEquals(3, booking.getStatusHistory().size());
    }

    @Test
    void cannotExpireHoldBeforeDeadline() {
        Booking booking = bookingWithSlots(List.of(
                slot("2030-01-02T01:00:00Z", "2030-01-02T03:00:00Z", "100000")));

        assertThrows(
                IllegalStateException.class,
                () -> booking.expire(NOW.plusSeconds(60)));
    }

    @Test
    void cannotStartPaymentAfterHoldExpired() {
        Booking booking = bookingWithSlots(List.of(
                slot("2030-01-02T01:00:00Z", "2030-01-02T03:00:00Z", "100000")));

        assertThrows(
                IllegalStateException.class,
                () -> booking.startPayment(NOW.plusSeconds(600)));
    }

    private Booking bookingWithSlots(List<BookingSlot.Spec> slots) {
        return Booking.hold(
                "BK-2030-0001",
                CUSTOMER_ID,
                "VND",
                "checkout-123",
                REQUEST_HASH,
                NOW.plusSeconds(600),
                NOW,
                slots);
    }

    private BookingSlot.Spec slot(String start, String end, String price) {
        return new BookingSlot.Spec(
                COURT_ID,
                Instant.parse(start),
                Instant.parse(end),
                new BigDecimal(price));
    }
}
