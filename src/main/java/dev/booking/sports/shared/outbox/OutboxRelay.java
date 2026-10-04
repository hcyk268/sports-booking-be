package dev.booking.sports.shared.outbox;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.booking.sports.shared.config.OutboxProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

	private final OutboxRepository repository;
	private final List<OutboxPublicationHandler> handlers;
	private final OutboxProperties properties;

	@Transactional
	public void dispatchPendingMessages() {
		for (OutboxEvent message : repository.lockPendingBatch(properties.batchSize())) {
			dispatch(message);
		}
	}

	private void dispatch(OutboxEvent message) {
		OutboxPublicationHandler handler = handlers.stream()
				.filter(candidate -> candidate.supports(message.getEventType()))
				.findFirst()
				.orElse(null);

		if (handler == null) {
			message.recordFailure(
					"No OutboxPublicationHandler for event type " + message.getEventType(),
					properties.maxRetries(),
					nextAvailableAt());
			log.error("Outbox event {} has no handler for type {}", message.getEventId(), message.getEventType());
			return;
		}

		try {
			handler.publish(message);
			message.markPublished(Instant.now());
		}
		catch (Exception exception) {
			message.recordFailure(exception.toString(), properties.maxRetries(), nextAvailableAt());
			log.warn("Outbox event {} failed on attempt {}: {}",
					message.getEventId(), message.getAttempts(), exception.getMessage(), exception);
		}
	}

	private Instant nextAvailableAt() {
		return Instant.now().plus(properties.pollInterval());
	}
}
