package dev.booking.sports.payment.api.controller;

import java.time.Instant;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;

import dev.booking.sports.payment.application.PaymentService;
import org.springframework.security.core.GrantedAuthority;
import lombok.RequiredArgsConstructor;
import dev.booking.sports.payment.application.PaymentQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import dev.booking.sports.payment.api.dto.response.PaymentCheckoutResponse;
import dev.booking.sports.payment.api.dto.response.PaymentReconciliationResponse;
import dev.booking.sports.payment.api.dto.response.PaymentResponse;
import dev.booking.sports.payment.domain.enums.PaymentStatus;
import dev.booking.sports.shared.api.ApiResponse;
import dev.booking.sports.shared.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Validated
@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentQueryService queryService;
    private final PaymentService paymentService;

    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'PROVIDER', 'ADMIN')")
    @Operation(summary = "List payments accessible to the current user")
    public ApiResponse<PageResponse<PaymentResponse>> listPayments(
            @AuthenticationPrincipal(expression = "userId()") UUID actorId,
            @RequestParam(required = false) UUID bookingId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(queryService.listPayments(actorId, authorities(authentication),
                bookingId, customerId, status, from, to, page, size), servletRequest.getRequestURI());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'PROVIDER', 'ADMIN')")
    @Operation(summary = "Get an accessible payment and its attempts, transactions and refunds")
    public ApiResponse<PaymentResponse> getPayment(
            @AuthenticationPrincipal(expression = "userId()") UUID actorId,
            @PathVariable UUID id,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(queryService.getPayment(actorId, authorities(authentication), id),
                servletRequest.getRequestURI());
    }

    @PostMapping("/{id}/checkout")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Resume the existing eligible checkout without extending the booking hold")
    public ApiResponse<PaymentCheckoutResponse> resumeCheckout(
            @AuthenticationPrincipal(expression = "userId()") UUID customerId,
            @PathVariable UUID id,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(paymentService.resumePayment(customerId, id), servletRequest.getRequestURI());
    }

    @PostMapping("/{id}/reconciliation")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Request reconciliation of a payment with the gateway")
    public ApiResponse<PaymentReconciliationResponse> reconcilePayment(
            @AuthenticationPrincipal(expression = "userId()") UUID adminId,
            @PathVariable UUID id,
            HttpServletRequest servletRequest) {
        return null;
    }

    private static Set<String> authorities(Authentication authentication) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }
}
