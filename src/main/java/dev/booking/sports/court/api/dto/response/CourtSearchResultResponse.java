package dev.booking.sports.court.api.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record CourtSearchResultResponse(
        CourtResponse court,
        Match match) {

    public record Match(
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            BigDecimal totalPrice,
            String currency) {
    }
}
