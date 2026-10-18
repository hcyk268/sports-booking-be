package dev.booking.sports.shared.upload;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import dev.booking.sports.shared.api.ApiResponse;
import dev.booking.sports.shared.ratelimit.RateLimit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import static dev.booking.sports.shared.upload.UploadContracts.*;

@RestController
@RequestMapping("/api/v1/uploads")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class UploadController {
    private final UploadService service;
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    @RateLimit(action = "upload-initiate", maxRequests = 20)
    public ApiResponse<Ticket> initiate(@AuthenticationPrincipal(expression = "userId()") UUID actor,
            @Valid @RequestBody InitiateRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.initiate(actor, body), request.getRequestURI());
    }
    @PostMapping("/{uploadId}/complete")
    @RateLimit(action = "upload-complete", maxRequests = 30)
    public ApiResponse<VerifiedUpload> complete(@AuthenticationPrincipal(expression = "userId()") UUID actor,
            @PathVariable UUID uploadId, HttpServletRequest request) {
        return ApiResponse.success(service.complete(actor, uploadId), request.getRequestURI());
    }
    @DeleteMapping("/{uploadId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal(expression = "userId()") UUID actor, @PathVariable UUID uploadId) {
        service.cancel(actor, uploadId);
    }
}
