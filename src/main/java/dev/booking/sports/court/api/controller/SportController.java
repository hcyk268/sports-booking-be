package dev.booking.sports.court.api.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.booking.sports.court.api.dto.response.SportResponse;
import dev.booking.sports.court.application.SportService;
import dev.booking.sports.shared.api.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/sports")
@RequiredArgsConstructor
@Tag(name = "Sport")
public class SportController {

    private final SportService sportService;

    @GetMapping
    @Operation(summary = "List available sports")
    public ApiResponse<List<SportResponse>> getSports(
            HttpServletRequest request) {

        return ApiResponse.success(
                sportService.getSports(),
                request.getRequestURI());
    }
}