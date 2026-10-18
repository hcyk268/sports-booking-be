package dev.booking.sports.court.api.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.booking.sports.court.api.dto.request.CreateCourtClosureRequest;
import dev.booking.sports.court.api.dto.response.CourtClosureResponse;
import dev.booking.sports.court.application.CourtClosureService;
import dev.booking.sports.shared.api.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/provider/courts/{id}/closures")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PROVIDER')")
@Tag(name = "Court Closures")
public class CourtClosureController {

    private final CourtClosureService service;

    @GetMapping
    @Operation(summary = "Get court closures for a date")
    public ApiResponse<List<CourtClosureResponse>> getClosures(
            @AuthenticationPrincipal(expression = "userId()")
            UUID ownerId,
            @PathVariable("id") UUID courtId,
            @RequestParam("date")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date,
            HttpServletRequest servletRequest) {

        return ApiResponse.success(
                service.getClosures(ownerId, courtId, date),
                servletRequest.getRequestURI());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a temporary closure for an owned court")
    public ApiResponse<CourtClosureResponse> createClosure(
            @AuthenticationPrincipal(expression = "userId()")
            UUID ownerId,
            @PathVariable("id") UUID courtId,
            @Valid @RequestBody CreateCourtClosureRequest request,
            HttpServletRequest servletRequest) {

        return ApiResponse.success(
                service.createClosure(ownerId, courtId, request),
                servletRequest.getRequestURI());
    }

    @DeleteMapping("/{closureId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a temporary closure from an owned court")
    public void deleteClosure(
            @AuthenticationPrincipal(expression = "userId()")
            UUID ownerId,
            @PathVariable("id") UUID courtId,
            @PathVariable("closureId") UUID closureId) {

        service.deleteClosure(ownerId, courtId, closureId);
    }
}