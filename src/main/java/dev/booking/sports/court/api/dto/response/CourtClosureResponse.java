package dev.booking.sports.court.api.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import dev.booking.sports.court.domain.model.CourtClosure;

public record CourtClosureResponse(
        UUID id,
        UUID courtId,
        LocalDate closureDate,
        LocalTime startTime,
        LocalTime endTime,
        String reason) {

    public static CourtClosureResponse from(CourtClosure closure) {
        return new CourtClosureResponse(
                closure.getId(),
                closure.getCourt().getId(),
                closure.getClosureDate(),
                closure.getStartTime(),
                closure.getEndTime(),
                closure.getReason());
    }
}