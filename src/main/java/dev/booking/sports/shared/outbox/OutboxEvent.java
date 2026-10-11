package dev.booking.sports.shared.outbox;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "outbox_events")
@NoArgsConstructor
public class OutboxEvent {

	@Id
	@Column(name = "event_id", nullable = false, updatable = false)
	private UUID eventId = UUID.randomUUID();

	@Column(name = "aggregate_type", nullable = false, length = 50)
	private String aggregateType;

	@Column(name = "aggregate_id", nullable = false, length = 100)
	private String aggregateId;

	@Column(name = "event_type", nullable = false, length = 200)
	private String eventType;

	@Column(name = "event_version", nullable = false)
	private int eventVersion;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private String payload;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OutboxStatus status = OutboxStatus.PENDING;

	@Column(nullable = false)
	private int attempts;

	@Column(name = "available_at", nullable = false)
	private Instant availableAt;

	@Column(name = "last_error", length = 2000)
	private String lastError;

	@Column(name = "published_at")
	private Instant publishedAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public OutboxEvent(
			String aggregateType,
			String aggregateId,
			String eventType,
			int eventVersion,
			String payload) {

		this.aggregateType = aggregateType;
		this.aggregateId = aggregateId;
		this.eventType = eventType;
		this.eventVersion = eventVersion;
		this.payload = payload;
		this.availableAt = Instant.now();
	}

	public void markPublished(Instant publishedAt) {
		this.attempts++;
		this.status = OutboxStatus.PUBLISHED;
		this.publishedAt = publishedAt;
		this.lastError = null;
	}

	public void recordFailure(String error, int maxRetries, Instant nextAvailableAt) {
		this.attempts++;
		this.lastError = truncate(error, 2000);
		if (this.attempts >= maxRetries) {
			this.status = OutboxStatus.FAILED;
			return;
		}

		this.availableAt = nextAvailableAt;
	}

	private static String truncate(String value, int maxLength) {
		if (value == null) {
			return null;
		}
		return value.length() <= maxLength ? value : value.substring(0, maxLength);
	}
}
