package dev.booking.sports.payment.api.controller;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import dev.booking.sports.payment.api.dto.request.CreatePaymentRequest;
import dev.booking.sports.payment.api.dto.response.PaymentCheckoutResponse;
import dev.booking.sports.payment.api.dto.response.PaymentResponse;
import dev.booking.sports.payment.application.BookingPaymentService;
import dev.booking.sports.payment.application.PaymentQueryService;
import org.springframework.security.core.GrantedAuthority;
import java.util.stream.Collectors;
import dev.booking.sports.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequestMapping("/api/v1/bookings/{bookingId}/payments")
@Tag(name = "Payment")
@RequiredArgsConstructor
public class BookingPaymentController {

    private final BookingPaymentService bookingPaymentService;
    private final PaymentQueryService paymentQueryService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Initialize payment and checkout for a held booking")
    public ApiResponse<PaymentCheckoutResponse> createPayment(
            @AuthenticationPrincipal(expression = "userId()") UUID customerId,
            @PathVariable UUID bookingId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(
                bookingPaymentService.createPayment(customerId, bookingId, idempotencyKey, request),
                servletRequest.getRequestURI());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'PROVIDER', 'ADMIN')")
    @Operation(summary = "Get payments and checkout availability for an accessible booking")
    public ApiResponse<List<PaymentResponse>> getBookingPayments(
            @AuthenticationPrincipal(expression = "userId()") UUID actorId,
            @PathVariable UUID bookingId,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(paymentQueryService.getBookingPayments(actorId,
                authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet()),
                bookingId), servletRequest.getRequestURI());
    }
}
