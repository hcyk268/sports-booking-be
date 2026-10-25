package dev.booking.sports.booking.api.dto.request;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateBookingHoldRequest(
        @NotNull UUID courtId,
        @NotEmpty @Size(max = 20)
        List<@NotNull @Valid Slot> slots) {

    public record Slot(
            @NotNull LocalDate date,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime) {
    }
}
