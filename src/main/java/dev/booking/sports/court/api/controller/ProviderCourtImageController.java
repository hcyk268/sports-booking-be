package dev.booking.sports.court.api.controller;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import dev.booking.sports.court.api.dto.response.CourtImageResponse;
import dev.booking.sports.court.application.CourtImageService;
import dev.booking.sports.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/provider/courts/{courtId}/images")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PROVIDER')")
@Tag(name = "Provider Court Images")
public class ProviderCourtImageController {

    private final CourtImageService service;

    @GetMapping
    @Operation(summary = "List images of an owned court")
    public ApiResponse<List<CourtImageResponse>> list(
            @AuthenticationPrincipal(expression = "userId()") UUID ownerId,
            @PathVariable("courtId") UUID courtId,
            HttpServletRequest request) {
        return ApiResponse.success(
                service.listOwned(ownerId, courtId), request.getRequestURI());
    }

    public record AttachImageRequest(@NotNull UUID uploadId) {}

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Attach an owned, verified COURT_IMAGE upload; no file bytes are accepted")
    public ApiResponse<CourtImageResponse> attach(
            @AuthenticationPrincipal(expression = "userId()") UUID ownerId,
            @PathVariable("courtId") UUID courtId,
            @Valid @RequestBody AttachImageRequest body,
            HttpServletRequest request) {
        return ApiResponse.success(service.attach(ownerId, courtId, body.uploadId()), request.getRequestURI());
    }

    @PutMapping("/{imageId}/cover")
    @Operation(summary = "Choose the cover image of an owned court")
    public ApiResponse<CourtImageResponse> setCover(
            @AuthenticationPrincipal(expression = "userId()") UUID ownerId,
            @PathVariable("courtId") UUID courtId,
            @PathVariable("imageId") UUID imageId,
            HttpServletRequest request) {
        return ApiResponse.success(
                service.setCover(ownerId, courtId, imageId), request.getRequestURI());
    }

    @DeleteMapping("/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an image from an owned court")
    public void delete(
            @AuthenticationPrincipal(expression = "userId()") UUID ownerId,
            @PathVariable("courtId") UUID courtId,
            @PathVariable("imageId") UUID imageId) {
        service.delete(ownerId, courtId, imageId);
    }

    @GetMapping("/{imageId}/access")
    @Operation(summary = "Get short-lived read access to an owned image; url is null for legacy local data")
    public ApiResponse<dev.booking.sports.shared.upload.UploadContracts.ReadAccess> access(
            @AuthenticationPrincipal(expression = "userId()") UUID ownerId,
            @PathVariable("courtId") UUID courtId,
            @PathVariable("imageId") UUID imageId,
            HttpServletRequest request) {
        return ApiResponse.success(service.accessOwned(ownerId, courtId, imageId), request.getRequestURI());
    }

    @GetMapping("/{imageId}/content")
    @Operation(summary = "View an image of an owned court, including draft courts")
    public ResponseEntity<byte[]> content(
            @AuthenticationPrincipal(expression = "userId()") UUID ownerId,
            @PathVariable("courtId") UUID courtId,
            @PathVariable("imageId") UUID imageId) {
        return CourtImageController.imageResponse(
                service.readOwned(ownerId, courtId, imageId));
    }
}
