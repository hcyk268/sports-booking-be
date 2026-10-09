package dev.booking.sports.notification.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.booking.sports.support.BaseIntegrationTest;
import dev.booking.sports.support.IntegrationTest;

@IntegrationTest
class EmailDeliveryDedupServiceTest extends BaseIntegrationTest {

	private static final UUID OUTBOX_EVENT_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

	@Autowired
	private EmailDeliveryDedupService deliveryDedupService;

	@Test
	void tryAcquire_allowsFirstDeliveryAndBlocksDuplicates() {
		assertThat(deliveryDedupService.tryAcquire(OUTBOX_EVENT_ID)).isTrue();
		assertThat(deliveryDedupService.tryAcquire(OUTBOX_EVENT_ID)).isFalse();
	}

	@Test
	void release_allowsAnotherDeliveryAttempt() {
		assertThat(deliveryDedupService.tryAcquire(OUTBOX_EVENT_ID)).isTrue();

		deliveryDedupService.release(OUTBOX_EVENT_ID);

		assertThat(deliveryDedupService.tryAcquire(OUTBOX_EVENT_ID)).isTrue();
	}
}
