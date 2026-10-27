package dev.booking.sports.booking.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.booking.sports.booking.domain.enums.BookingStatus;
import dev.booking.sports.booking.domain.model.Booking;

public record BookingResponse(
        UUID id,
        String bookingCode,
        UUID customerId,
        BigDecimal totalAmount,
        String currency,
        BookingStatus status,
        Instant holdExpiresAt,
        Instant confirmedAt,
        UUID cancelledBy,
        Instant cancelledAt,
        String cancellationReason,
        Instant completedAt,
        Long version,
        Instant createdAt,
        List<BookingSlotResponse> slots) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getBookingCode(),
                booking.getCustomerId(),
                booking.getTotalAmount(),
                booking.getCurrency(),
                booking.getStatus(),
                booking.getHoldExpiresAt(),
                booking.getConfirmedAt(),
                booking.getCancelledBy(),
                booking.getCancelledAt(),
                booking.getCancellationReason(),
                booking.getCompletedAt(),
                booking.getVersion(),
                booking.getCreatedAt(),
                booking.getSlots().stream()
                        .map(BookingSlotResponse::from)
                        .toList());
    }
}
