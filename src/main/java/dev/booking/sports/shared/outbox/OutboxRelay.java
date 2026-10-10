package dev.booking.sports.shared.outbox;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import dev.booking.sports.shared.config.OutboxProperties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class OutboxRelay {

	private final OutboxRepository repository;
	private final List<OutboxPublicationHandler> handlers;
	private final OutboxProperties properties;
	private final TransactionTemplate transactionTemplate;

	public OutboxRelay(
			OutboxRepository repository,
			List<OutboxPublicationHandler> handlers,
			OutboxProperties properties,
			PlatformTransactionManager transactionManager) {

		this.repository = repository;
		this.handlers = handlers;
		this.properties = properties;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	public void dispatchPendingMessages() {
		for (int index = 0; index < properties.batchSize(); index++) {
			Boolean dispatched = transactionTemplate.execute(status -> {
				List<OutboxEvent> batch = repository.lockPendingBatch(1);
				if (batch.isEmpty()) {
					return false;
				}

				dispatch(batch.getFirst());
				return true;
			});

			if (!Boolean.TRUE.equals(dispatched)) {
				break;
			}
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
