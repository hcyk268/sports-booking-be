package dev.booking.sports.court.publicapi;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record CourtSlotQuote(
        UUID courtId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        BigDecimal priceAmount,
        String currency) {
}
