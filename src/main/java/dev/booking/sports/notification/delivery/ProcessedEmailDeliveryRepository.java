package dev.booking.sports.notification.delivery;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEmailDeliveryRepository extends JpaRepository<ProcessedEmailDelivery, UUID> {

	@Modifying
	@Query(
			value = """
					INSERT INTO processed_email_deliveries (outbox_event_id)
					VALUES (:outboxEventId)
					ON CONFLICT (outbox_event_id) DO NOTHING
					""",
			nativeQuery = true
	)
	int insertIfAbsent(@Param("outboxEventId") UUID outboxEventId);
}
