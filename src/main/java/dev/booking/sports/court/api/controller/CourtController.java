package dev.booking.sports.court.api.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.booking.sports.court.api.dto.request.CreateCourtRequest;
import dev.booking.sports.court.api.dto.request.UpdateCourtRequest;
import dev.booking.sports.court.api.dto.response.CourtResponse;
import dev.booking.sports.court.application.CourtService;
import dev.booking.sports.shared.api.ApiResponse;
import dev.booking.sports.shared.api.PageResponse;

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

    @GetMapping
    @Operation(summary = "Search public courts")
    public ApiResponse<PageResponse<CourtResponse>> searchCourts(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "city", required = false) String city,
            @RequestParam(name = "sportId", required = false) UUID sportId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            HttpServletRequest servletRequest) {

        return ApiResponse.success(
                courtService.searchPublicCourts(
                        keyword, city, sportId, page, size),
                servletRequest.getRequestURI());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get public court details")
    public ApiResponse<CourtResponse> getCourt(
            @PathVariable("id") UUID courtId,
            HttpServletRequest servletRequest) {

        return ApiResponse.success(
                courtService.getPublicCourt(courtId),
                servletRequest.getRequestURI());
    }

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

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('PROVIDER')")
    @Operation(summary = "Publish a draft court owned by the current provider")
    public ApiResponse<CourtResponse> publishCourt(
            @AuthenticationPrincipal(expression = "userId()") UUID ownerId,
            @PathVariable("id") UUID courtId,
            HttpServletRequest servletRequest) {

        return ApiResponse.success(
                courtService.publishCourt(ownerId, courtId),
                servletRequest.getRequestURI());
    }
}