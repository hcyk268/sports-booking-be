package dev.booking.sports.court.api.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.booking.sports.court.api.dto.request.CreateCourtRequest;
import dev.booking.sports.court.api.dto.request.UpdateCourtRequest;
import dev.booking.sports.court.api.dto.response.CourtResponse;
import dev.booking.sports.court.application.CourtService;
import dev.booking.sports.shared.api.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/courts")
@RequiredArgsConstructor
@Tag(name = "Court")
public class CourtController {

    private final CourtService courtService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PROVIDER')")
    @Operation(summary = "Create a draft court for the current provider")
    public ApiResponse<CourtResponse> createCourt(
            @AuthenticationPrincipal(expression = "userId()") UUID ownerId,
            @Valid @RequestBody CreateCourtRequest request,
            HttpServletRequest servletRequest) {

        return ApiResponse.success(
                courtService.createCourt(ownerId, request),
                servletRequest.getRequestURI());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PROVIDER')")
    @Operation(summary = "Update a court owned by the current provider")
    public ApiResponse<CourtResponse> updateCourt(
            @AuthenticationPrincipal(expression = "userId()") UUID ownerId,
            @PathVariable("id") UUID courtId,
            @Valid @RequestBody UpdateCourtRequest request,
            HttpServletRequest servletRequest) {

        return ApiResponse.success(
                courtService.updateCourt(ownerId, courtId, request),
                servletRequest.getRequestURI());
    }
}