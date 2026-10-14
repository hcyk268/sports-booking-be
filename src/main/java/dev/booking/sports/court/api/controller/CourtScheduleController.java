package dev.booking.sports.court.api.controller;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.booking.sports.court.api.dto.response.CourtScheduleResponse;
import dev.booking.sports.court.application.CourtScheduleService;
import dev.booking.sports.shared.api.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/courts")
@RequiredArgsConstructor
@Tag(name = "Court Schedule")
public class CourtScheduleController {

    private final CourtScheduleService service;

    @GetMapping("/{id}/schedule")
    @Operation(
            summary = "Get daily court slots and prices",
            description = "Booking availability is not checked by this endpoint")
    public ApiResponse<CourtScheduleResponse> getSchedule(
            @PathVariable("id") UUID courtId,
            @RequestParam("date")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date,
            HttpServletRequest servletRequest) {

        return ApiResponse.success(
                service.getSchedule(courtId, date),
                servletRequest.getRequestURI());
    }
}