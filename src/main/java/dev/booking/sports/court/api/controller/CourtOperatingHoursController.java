package dev.booking.sports.court.api.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.booking.sports.court.api.dto.request.UpdateOperatingHoursRequest;
import dev.booking.sports.court.api.dto.response.OperatingHoursResponse;
import dev.booking.sports.court.application.CourtOperatingHoursService;
import dev.booking.sports.shared.api.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/provider/courts/{id}/operating-hours")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PROVIDER')")
@Tag(name = "Court Operating Hours")
public class CourtOperatingHoursController {

    private final CourtOperatingHoursService service;

    @GetMapping
    @Operation(summary = "Get operating hours of an owned court")
    public ApiResponse<List<OperatingHoursResponse>> getOperatingHours(
            @AuthenticationPrincipal(expression = "userId()")
            UUID ownerId,
            @PathVariable("id") UUID courtId,
            HttpServletRequest servletRequest) {

        return ApiResponse.success(
                service.getOperatingHours(ownerId, courtId),
                servletRequest.getRequestURI());
    }

    @PutMapping
    @Operation(summary = "Replace the weekly schedule of an owned court")
    public ApiResponse<List<OperatingHoursResponse>> updateOperatingHours(
            @AuthenticationPrincipal(expression = "userId()")
            UUID ownerId,
            @PathVariable("id") UUID courtId,
            @Valid @RequestBody UpdateOperatingHoursRequest request,
            HttpServletRequest servletRequest) {

        return ApiResponse.success(
                service.updateOperatingHours(
                        ownerId, courtId, request),
                servletRequest.getRequestURI());
    }
}