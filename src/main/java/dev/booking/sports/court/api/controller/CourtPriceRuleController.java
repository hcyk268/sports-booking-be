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

import dev.booking.sports.court.api.dto.request.UpdatePriceRulesRequest;
import dev.booking.sports.court.api.dto.response.PriceRuleResponse;
import dev.booking.sports.court.application.CourtPriceRuleService;
import dev.booking.sports.shared.api.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/provider/courts/{id}/price-rules")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PROVIDER')")
@Tag(name = "Court Price Rules")
public class CourtPriceRuleController {

    private final CourtPriceRuleService service;

    @GetMapping
    @Operation(summary = "Get price rules of an owned court")
    public ApiResponse<List<PriceRuleResponse>> getPriceRules(
            @AuthenticationPrincipal(expression = "userId()")
            UUID ownerId,
            @PathVariable("id") UUID courtId,
            HttpServletRequest servletRequest) {

        return ApiResponse.success(
                service.getPriceRules(ownerId, courtId),
                servletRequest.getRequestURI());
    }

    @PutMapping
    @Operation(summary = "Replace all price rules of an owned court")
    public ApiResponse<List<PriceRuleResponse>> updatePriceRules(
            @AuthenticationPrincipal(expression = "userId()")
            UUID ownerId,
            @PathVariable("id") UUID courtId,
            @Valid @RequestBody UpdatePriceRulesRequest request,
            HttpServletRequest servletRequest) {

        return ApiResponse.success(
                service.updatePriceRules(
                        ownerId, courtId, request),
                servletRequest.getRequestURI());
    }
}