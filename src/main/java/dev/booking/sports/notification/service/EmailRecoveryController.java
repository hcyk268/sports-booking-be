package dev.booking.sports.notification.service;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/notifications/email-dlq")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class EmailRecoveryController {
    private final EmailRecoveryService service;

    @PostMapping("/replay")
    public ResponseEntity<Map<String, Integer>> replay(@RequestParam(defaultValue = "25") int limit) {
        if (limit < 1 || limit > 100) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(Map.of("replayed", service.replayDeadLetters(limit)));
    }
}
