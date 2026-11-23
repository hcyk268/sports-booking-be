package dev.booking.sports.payment.api.controller;

import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import dev.booking.sports.payment.api.dto.request.CompleteRefundRequest;
import dev.booking.sports.payment.api.dto.request.CreateRefundRequest;
import dev.booking.sports.payment.api.dto.request.RefundDecisionRequest;
import dev.booking.sports.payment.api.dto.request.RefundVersionRequest;
import dev.booking.sports.payment.api.dto.response.PaymentRefundResponse;
import dev.booking.sports.payment.domain.enums.PaymentRefundStatus;
import dev.booking.sports.shared.api.ApiResponse;
import dev.booking.sports.shared.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Validated
@RestController
@RequestMapping("/api/v1/payments/refunds")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Payment Refund")
@lombok.RequiredArgsConstructor
public class PaymentRefundController {
    private final dev.booking.sports.payment.application.PaymentRefundService refunds;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a manual refund request for a collected transaction")
    public ApiResponse<PaymentRefundResponse> createRefund(
            @AuthenticationPrincipal(expression = "userId()") UUID adminId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody CreateRefundRequest request,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(refunds.create(adminId, idempotencyKey, request), servletRequest.getRequestURI());
    }

    @GetMapping
    @Operation(summary = "List manual refunds for admin processing")
    public ApiResponse<PageResponse<PaymentRefundResponse>> listRefunds(
            @AuthenticationPrincipal(expression = "userId()") UUID adminId,
            @RequestParam(required = false) UUID paymentId,
            @RequestParam(required = false) UUID paymentTransactionId,
            @RequestParam(required = false) PaymentRefundStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(refunds.list(paymentId, paymentTransactionId, status, from, to, page, size), servletRequest.getRequestURI());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get manual refund details")
    public ApiResponse<PaymentRefundResponse> getRefund(
            @AuthenticationPrincipal(expression = "userId()") UUID adminId,
            @PathVariable UUID id,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(refunds.get(id), servletRequest.getRequestURI());
    }

    @PostMapping("/{id}/processing")
    @Operation(summary = "Start processing a manual refund")
    public ApiResponse<PaymentRefundResponse> startRefundProcessing(
            @AuthenticationPrincipal(expression = "userId()") UUID adminId,
            @PathVariable UUID id,
            @Valid @RequestBody RefundVersionRequest request,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(refunds.start(id, adminId, request.version()), servletRequest.getRequestURI());
    }

    @PostMapping("/{id}/completion")
    @Operation(summary = "Record a completed manual refund and its transfer reference")
    public ApiResponse<PaymentRefundResponse> completeRefund(
            @AuthenticationPrincipal(expression = "userId()") UUID adminId,
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody CompleteRefundRequest request,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(refunds.complete(id, adminId, idempotencyKey, request), servletRequest.getRequestURI());
    }

    @PostMapping("/{id}/rejection")
    @Operation(summary = "Reject a refund before processing starts")
    public ApiResponse<PaymentRefundResponse> rejectRefund(
            @AuthenticationPrincipal(expression = "userId()") UUID adminId,
            @PathVariable UUID id,
            @Valid @RequestBody RefundDecisionRequest request,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(refunds.decide(id, adminId, request, true), servletRequest.getRequestURI());
    }

    @PostMapping("/{id}/cancellation")
    @Operation(summary = "Cancel a refund request before processing starts")
    public ApiResponse<PaymentRefundResponse> cancelRefund(
            @AuthenticationPrincipal(expression = "userId()") UUID adminId,
            @PathVariable UUID id,
            @Valid @RequestBody RefundDecisionRequest request,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(refunds.decide(id, adminId, request, false), servletRequest.getRequestURI());
    }
}
