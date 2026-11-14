package dev.booking.sports.notification.email;

import java.math.BigDecimal;
import java.util.List;
import dev.booking.sports.booking.domain.event.BookingSlotSnapshot;

public record BookingEmailDetails(String bookingCode, BigDecimal totalAmount, String currency,
        List<BookingSlotSnapshot> slots, String cancellationReason) {}
