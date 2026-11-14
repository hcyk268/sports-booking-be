package dev.booking.sports.notification.api.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import dev.booking.sports.identity.publicapi.StreamSessionVerifier;
import dev.booking.sports.notification.realtime.NotificationSseRegistry;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class NotificationStreamController {

    private final StreamSessionVerifier sessions;
    private final NotificationSseRegistry registry;

    @GetMapping(value = "/api/v1/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Open a local notification SSE stream for the authenticated access token")
    public ResponseEntity<SseEmitter> stream(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        var emitter = registry.open(sessions.authenticate(authorization));
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Accel-Buffering", "no").contentType(MediaType.TEXT_EVENT_STREAM).body(emitter);
    }
}
