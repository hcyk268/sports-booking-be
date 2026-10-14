package dev.booking.sports.court.api.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record CourtScheduleResponse(
        UUID courtId,
        LocalDate date,
        String currency,
        List<Slot> slots) {

    public record Slot(
            LocalTime startTime,
            LocalTime endTime,
            BigDecimal totalPrice,
            boolean priceConfigured) {
    }
}