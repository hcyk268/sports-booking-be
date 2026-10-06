package dev.booking.sports.shared.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import dev.booking.sports.identity.domain.event.EmailVerificationRequestedEvent;
import dev.booking.sports.identity.domain.event.IdentityOutboxMessageTypes;
import dev.booking.sports.support.BaseIntegrationTest;
import dev.booking.sports.support.IntegrationTest;

@IntegrationTest
@Transactional
class OutboxServiceIntegrationTest extends BaseIntegrationTest {

	@Autowired
	private OutboxService outboxService;

	@Autowired
	private OutboxRepository outboxRepository;

	@Test
	void enqueue_persistsPendingEventWithJsonPayload() {
		UUID userId = UUID.fromString("66666666-6666-6666-6666-666666666666");
		var payload = new EmailVerificationRequestedEvent(
				userId,
				"outbox-it@example.com",
				"Outbox IT",
				"https://localhost/verify");

		outboxService.enqueue(
				IdentityOutboxMessageTypes.USER_AGGREGATE_TYPE,
				userId,
				IdentityOutboxMessageTypes.EMAIL_VERIFICATION_REQUESTED,
				IdentityOutboxMessageTypes.EVENT_VERSION,
				payload);

		OutboxEvent stored = outboxRepository
				.findFirstByEventTypeOrderByCreatedAtDesc(IdentityOutboxMessageTypes.EMAIL_VERIFICATION_REQUESTED)
				.orElseThrow();

		assertThat(stored.getStatus()).isEqualTo(OutboxStatus.PENDING);
		assertThat(stored.getAggregateId()).isEqualTo(userId.toString());
		assertThat(stored.getPayload()).contains("outbox-it@example.com");
		assertThat(stored.getPayload()).contains("Outbox IT");
	}
}
