package dev.booking.sports.court.api.controller;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.booking.sports.court.api.dto.response.CourtResponse;
import dev.booking.sports.court.application.ProviderCourtService;
import dev.booking.sports.shared.api.ApiResponse;
import dev.booking.sports.shared.api.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/provider/courts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PROVIDER')")
@Tag(name = "Provider Court")
public class ProviderCourtController {

    private final ProviderCourtService providerCourtService;

    @GetMapping
    @Operation(summary = "List courts owned by the current provider")
    public ApiResponse<PageResponse<CourtResponse>> getMyCourts(
            @AuthenticationPrincipal(expression = "userId()")
            UUID ownerId,
            @RequestParam(name = "page", defaultValue = "0")
            int page,
            @RequestParam(name = "size", defaultValue = "20")
            int size,
            HttpServletRequest servletRequest) {

        return ApiResponse.success(
                providerCourtService.getMyCourts(
                        ownerId, page, size),
                servletRequest.getRequestURI());
    }
}