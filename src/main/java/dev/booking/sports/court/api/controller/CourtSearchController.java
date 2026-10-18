package dev.booking.sports.court.api.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import dev.booking.sports.court.api.dto.response.CourtSearchResultResponse;
import dev.booking.sports.court.application.CourtSearchService;
import dev.booking.sports.shared.api.ApiResponse;
import dev.booking.sports.shared.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/courts")
@RequiredArgsConstructor
@Tag(name = "Court Search")
public class CourtSearchController {

    private final CourtSearchService service;

    @GetMapping("/search")
    @Operation(
            summary = "Find courts by date, operating hours and total slot price",
            description = "Excludes closures and missing prices. Booking conflicts are not checked. "
                    + "Time and price filters require date. A time range must contain complete consecutive slots. "
                    + "Prices are totals in VND for the requested range; without a range, matches one slot.")
    public ApiResponse<PageResponse<CourtSearchResultResponse>> search(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "city", required = false) String city,
            @RequestParam(name = "sportId", required = false) UUID sportId,
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "startTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
            @RequestParam(name = "endTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime,
            @RequestParam(name = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(name = "maxPrice", required = false) BigDecimal maxPrice,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            HttpServletRequest request) {
        return ApiResponse.success(
                service.search(keyword, city, sportId, date, startTime, endTime,
                        minPrice, maxPrice, page, size),
                request.getRequestURI());
    }
}
