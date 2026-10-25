package dev.booking.sports.court.publicapi;

import java.time.LocalDate;
import java.time.LocalTime;

public record CourtSlotSelection(
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime) {
}
