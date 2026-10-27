package dev.booking.sports.booking.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import dev.booking.sports.booking.domain.enums.BookingSlotStatus;
import dev.booking.sports.booking.domain.model.BookingSlot;

public record BookingSlotResponse(
        UUID id,
        UUID courtId,
        Instant startAt,
        Instant endAt,
        BigDecimal priceAmount,
        String currency,
        BookingSlotStatus status,
        Long version) {

    public static BookingSlotResponse from(BookingSlot slot) {
        return new BookingSlotResponse(
                slot.getId(),
                slot.getCourtId(),
                slot.getStartAt(),
                slot.getEndAt(),
                slot.getPriceAmount(),
                slot.getCurrency(),
                slot.getStatus(),
                slot.getVersion());
    }
}
