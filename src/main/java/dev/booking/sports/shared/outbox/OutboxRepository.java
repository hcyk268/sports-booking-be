package dev.booking.sports.shared.outbox;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

	Optional<OutboxEvent> findFirstByEventTypeOrderByCreatedAtDesc(String eventType);

	long countByEventType(String eventType);

	@Query("""
			SELECT *
			FROM outbox_events
			WHERE status = 'PENDING'
			  AND available_at <= CURRENT_TIMESTAMP
			ORDER BY available_at, created_at
			LIMIT :limit
			FOR UPDATE SKIP LOCKED
			""")
	List<OutboxEvent> lockPendingBatch(int limit);
}
