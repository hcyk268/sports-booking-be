package dev.booking.sports.court.api.dto.request;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateOperatingHoursRequest(
        @NotNull
        @Size(max = 7)
        List<@NotNull @Valid DaySchedule> days) {

    public record DaySchedule(
            @NotNull DayOfWeek dayOfWeek,
            @NotNull LocalTime openingTime,
            @NotNull LocalTime closingTime,
            @NotNull @Min(15) @Max(240)
            Integer slotDurationMinutes) {
    }
}