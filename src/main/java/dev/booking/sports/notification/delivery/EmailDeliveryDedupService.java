package dev.booking.sports.notification.delivery;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailDeliveryDedupService {

	private final ProcessedEmailDeliveryRepository repository;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean tryAcquire(UUID outboxEventId) {
		try {
			repository.saveAndFlush(new ProcessedEmailDelivery(outboxEventId));
			return true;
		}
		catch (DataIntegrityViolationException exception) {
			return false;
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void release(UUID outboxEventId) {
		repository.deleteById(outboxEventId);
	}
}
