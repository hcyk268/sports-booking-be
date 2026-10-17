package dev.booking.sports.court.api.controller;

import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import dev.booking.sports.court.api.dto.response.CourtImageResponse;
import dev.booking.sports.court.application.CourtImageService;
import dev.booking.sports.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/courts/{courtId}/images")
@RequiredArgsConstructor
@Tag(name = "Court Images")
public class CourtImageController {

    private final CourtImageService service;

    @GetMapping
    @Operation(summary = "List images of an active public court")
    public ApiResponse<List<CourtImageResponse>> list(
            @PathVariable("courtId") UUID courtId,
            HttpServletRequest request) {
        return ApiResponse.success(
                service.listPublic(courtId), request.getRequestURI());
    }

    @GetMapping("/{imageId}/content")
    @Operation(summary = "View an image of an active public court")
    public ResponseEntity<byte[]> content(
            @PathVariable("courtId") UUID courtId,
            @PathVariable("imageId") UUID imageId) {
        return imageResponse(service.readPublic(courtId, imageId));
    }

    static ResponseEntity<byte[]> imageResponse(CourtImageService.ImageContent content) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .contentLength(content.bytes().length)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + content.fileName() + "\"")
                .body(content.bytes());
    }
}
