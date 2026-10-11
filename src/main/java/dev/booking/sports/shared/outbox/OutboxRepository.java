package dev.booking.sports.shared.outbox;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

	Optional<OutboxEvent> findFirstByEventTypeOrderByCreatedAtDesc(String eventType);

	long countByEventType(String eventType);

	@Query(value = """
			SELECT oe.*
			FROM outbox_events oe
			WHERE oe.status = 'PENDING'
			  AND oe.available_at <= CURRENT_TIMESTAMP
			ORDER BY oe.available_at, oe.created_at
			LIMIT :limit
			FOR UPDATE SKIP LOCKED
			""", nativeQuery = true)
	List<OutboxEvent> lockPendingBatch(@Param("limit") int limit);
}
