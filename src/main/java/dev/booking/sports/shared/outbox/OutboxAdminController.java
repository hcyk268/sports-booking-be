package dev.booking.sports.shared.outbox;

import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/outbox")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class OutboxAdminController {
    private final OutboxRepository repository;

    @GetMapping("/stats")
    public Map<String, Long> stats() {
        return Map.of("pending", repository.countByStatus(OutboxStatus.PENDING),
                "failed", repository.countByStatus(OutboxStatus.FAILED));
    }

    @GetMapping("/failed")
    public ResponseEntity<java.util.List<FailedEvent>> failed(@RequestParam(defaultValue = "0") int page) {
        if (page < 0) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(repository.findByStatus(OutboxStatus.FAILED,
                PageRequest.of(page, 50, Sort.by("createdAt").descending()))
                .map(e -> new FailedEvent(e.getEventId(), e.getEventType(), e.getAttempts())).getContent());
    }

    @PostMapping("/{eventId}/replay")
    public ResponseEntity<Void> replay(@PathVariable UUID eventId) {
        return repository.replayFailed(eventId) == 1
                ? ResponseEntity.accepted().build() : ResponseEntity.status(409).build();
    }

    public record FailedEvent(UUID eventId, String eventType, int attempts) {}
}
