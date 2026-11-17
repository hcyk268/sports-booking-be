package dev.booking.sports.review;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import dev.booking.sports.shared.api.ApiResponse;
import dev.booking.sports.shared.api.PageResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
class ReviewController {
    private final ReviewService service;
    record Create(@NotNull UUID courtId, @NotNull @Min(1) @Max(5) Integer stars, @NotBlank @Size(max=2000) String comment) {}
    record Edit(@NotNull @PositiveOrZero Long version, @NotNull @Min(1) @Max(5) Integer stars, @NotBlank @Size(max=2000) String comment) {}
    record Reply(@NotNull @PositiveOrZero Long version, @NotBlank @Size(max=2000) String text) {}
    record Moderate(@NotNull @PositiveOrZero Long version, @NotNull Boolean hidden, @NotBlank @Size(max=500) String reason) {}

    @GetMapping("/courts/{court}/reviews")
    public ApiResponse<ReviewService.Feed> list(@PathVariable UUID court, @RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size, HttpServletRequest request) {
        return ApiResponse.success(service.list(court, page, size), request.getRequestURI());
    }

    @GetMapping("/admin/reviews")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<ReviewService.Managed>> moderationQueue(@RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size, HttpServletRequest request) {
        return ApiResponse.success(service.moderationQueue(page, size), request.getRequestURI());
    }

    @GetMapping("/bookings/{booking}/review")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<ReviewService.Managed> own(@PathVariable UUID booking, @AuthenticationPrincipal(expression="userId()") UUID actor,
            HttpServletRequest request) {
        return ApiResponse.success(service.own(booking, actor), request.getRequestURI());
    }

    @PostMapping("/bookings/{booking}/review")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<ReviewService.Managed> create(@PathVariable UUID booking, @AuthenticationPrincipal(expression="userId()") UUID actor,
            @Valid @RequestBody Create body, HttpServletRequest request) {
        return ApiResponse.success(service.create(booking, body.courtId(), actor, body.stars(), body.comment()), request.getRequestURI());
    }

    @PutMapping("/reviews/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<ReviewService.Managed> edit(@PathVariable UUID id, @AuthenticationPrincipal(expression="userId()") UUID actor,
            @Valid @RequestBody Edit body, HttpServletRequest request) {
        return ApiResponse.success(service.edit(id, actor, body.version(), body.stars(), body.comment()), request.getRequestURI());
    }

    @DeleteMapping("/reviews/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('CUSTOMER')")
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal(expression="userId()") UUID actor,
            @RequestParam @Min(0) long version) {
        service.delete(id, actor, version);
    }

    @PutMapping("/provider/reviews/{id}/reply")
    @PreAuthorize("hasRole('PROVIDER')")
    public ApiResponse<ReviewService.Entry> reply(@PathVariable UUID id, @AuthenticationPrincipal(expression="userId()") UUID actor,
            @Valid @RequestBody Reply body, HttpServletRequest request) {
        return ApiResponse.success(service.reply(id, actor, body.version(), body.text()), request.getRequestURI());
    }

    @PutMapping("/admin/reviews/{id}/visibility")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ReviewService.Managed> moderate(@PathVariable UUID id, @AuthenticationPrincipal(expression="userId()") UUID actor,
            @Valid @RequestBody Moderate body, HttpServletRequest request) {
        return ApiResponse.success(service.moderate(id, actor, body.version(), body.hidden(), body.reason()), request.getRequestURI());
    }
}
