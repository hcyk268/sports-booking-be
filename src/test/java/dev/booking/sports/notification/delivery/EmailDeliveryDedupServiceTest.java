package dev.booking.sports.notification.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.booking.sports.support.BaseIntegrationTest;
import dev.booking.sports.support.IntegrationTest;

@IntegrationTest
class EmailDeliveryDedupServiceTest extends BaseIntegrationTest {

	@Autowired
	private EmailDeliveryDedupService deliveryDedupService;

	@Test
	void tryAcquire_allowsFirstDeliveryAndBlocksDuplicates() {
		UUID outboxEventId = UUID.randomUUID();

		assertThat(deliveryDedupService.tryAcquire(outboxEventId)).isTrue();
		assertThat(deliveryDedupService.tryAcquire(outboxEventId)).isFalse();
	}

	@Test
	void release_allowsAnotherDeliveryAttempt() {
		UUID outboxEventId = UUID.randomUUID();

		assertThat(deliveryDedupService.tryAcquire(outboxEventId)).isTrue();

		deliveryDedupService.release(outboxEventId);

		assertThat(deliveryDedupService.tryAcquire(outboxEventId)).isTrue();
	}
}
