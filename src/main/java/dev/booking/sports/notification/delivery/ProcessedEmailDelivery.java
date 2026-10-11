package dev.booking.sports.notification.delivery;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "processed_email_deliveries")
@NoArgsConstructor
public class ProcessedEmailDelivery {

	@Id
	@Column(name = "outbox_event_id", nullable = false, updatable = false)
	private UUID outboxEventId;

	@CreationTimestamp
	@Column(name = "processed_at", nullable = false, updatable = false)
	private Instant processedAt;

	public ProcessedEmailDelivery(UUID outboxEventId) {
		this.outboxEventId = outboxEventId;
	}
}
