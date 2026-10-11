package dev.booking.sports.court.api.dto.response;

import java.time.DayOfWeek;
import java.time.LocalTime;

import dev.booking.sports.court.domain.model.CourtOperatingHours;

public record OperatingHoursResponse(
        DayOfWeek dayOfWeek,
        LocalTime openingTime,
        LocalTime closingTime,
        int slotDurationMinutes) {

    public static OperatingHoursResponse from(
            CourtOperatingHours hours) {

        return new OperatingHoursResponse(
                hours.getDayOfWeek(),
                hours.getOpeningTime(),
                hours.getClosingTime(),
                hours.getSlotDurationMinutes());
    }
}