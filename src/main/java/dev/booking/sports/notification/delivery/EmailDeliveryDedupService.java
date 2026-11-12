package dev.booking.sports.notification.delivery;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailDeliveryDedupService {

	private final ProcessedEmailDeliveryRepository repository;

	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public boolean deliverOnce(UUID outboxEventId, Runnable send) {
		if (repository.insertIfAbsent(outboxEventId) == 0) {
			return false;
		}
		send.run();
		return true;
	}
}
