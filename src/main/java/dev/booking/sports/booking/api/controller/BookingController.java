package dev.booking.sports.booking.api.controller;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.booking.sports.booking.api.dto.request.CancelBookingRequest;
import dev.booking.sports.booking.api.dto.request.CreateBookingHoldRequest;
import dev.booking.sports.booking.api.dto.response.BookingResponse;
import dev.booking.sports.booking.application.BookingActorRole;
import dev.booking.sports.booking.application.BookingCancellationService;
import dev.booking.sports.booking.application.BookingCreateService;
import dev.booking.sports.booking.application.BookingQueryService;
import dev.booking.sports.booking.application.BookingViewer;
import dev.booking.sports.booking.domain.enums.BookingStatus;
import dev.booking.sports.shared.api.ApiResponse;
import dev.booking.sports.shared.api.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequestMapping("/api/v1/bookings")
@Tag(name = "Booking")
@RequiredArgsConstructor
public class BookingController {

    private final BookingQueryService bookingQueryService;
    private final BookingCreateService bookingCreateService;
    private final BookingCancellationService bookingCancellationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Create a booking hold for selected court slots")
    public ApiResponse<BookingResponse> createBooking(
            @AuthenticationPrincipal(expression = "userId()") UUID customerId,
            @RequestHeader("Idempotency-Key")
            @NotBlank @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody CreateBookingHoldRequest request,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(
                bookingCreateService.createBooking(customerId, idempotencyKey, request),
                servletRequest.getRequestURI());
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "List bookings for the current customer")
    public ApiResponse<PageResponse<BookingResponse>> listMyBookings(
            @AuthenticationPrincipal(expression = "userId()") UUID customerId,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(
                bookingQueryService.listMyBookings(
                        customerId, status, date, page, size),
                servletRequest.getRequestURI());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PROVIDER', 'ADMIN')")
    @Operation(summary = "List bookings manageable by the current provider or admin")
    public ApiResponse<PageResponse<BookingResponse>> listBookings(
            @AuthenticationPrincipal(expression = "userId()") UUID actorId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID courtId,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(
                bookingQueryService.listBookings(
                        new BookingViewer(actorId, bookingRolesOf(authentication)),
                        customerId, courtId, status,
                        date, page, size),
                servletRequest.getRequestURI());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'PROVIDER', 'ADMIN')")
    @Operation(summary = "Get a booking accessible to the current user")
    public ApiResponse<BookingResponse> getBooking(
            @AuthenticationPrincipal(expression = "userId()") UUID actorId,
            @PathVariable("id") UUID bookingId,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(
                bookingQueryService.getBooking(
                        new BookingViewer(actorId, bookingRolesOf(authentication)), bookingId),
                servletRequest.getRequestURI());
    }

    @PostMapping("/{id}/cancellations")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'PROVIDER', 'ADMIN')")
    @Operation(summary = "Cancel a booking accessible to the current user")
    public ApiResponse<BookingResponse> cancelBooking(
            @AuthenticationPrincipal(expression = "userId()") UUID actorId,
            @PathVariable("id") UUID bookingId,
            @Valid @RequestBody CancelBookingRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(
                bookingCancellationService.cancelBooking(
                        new BookingViewer(actorId, bookingRolesOf(authentication)),
                        bookingId,
                        request),
                servletRequest.getRequestURI());
    }

    private Set<BookingActorRole> bookingRolesOf(
            Authentication authentication) {
        EnumSet<BookingActorRole> roles =
                EnumSet.noneOf(BookingActorRole.class);
        authentication.getAuthorities().forEach(authority -> {
            switch (authority.getAuthority()) {
                case "ROLE_CUSTOMER" -> roles.add(BookingActorRole.CUSTOMER);
                case "ROLE_PROVIDER" -> roles.add(BookingActorRole.PROVIDER);
                case "ROLE_ADMIN" -> roles.add(BookingActorRole.ADMIN);
                default -> {
                    // Permissions are not booking actor roles.
                }
            }
        });
        return roles;
    }
}
