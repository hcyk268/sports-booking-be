package dev.booking.sports.shared.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import dev.booking.sports.shared.outbox.OutboxRelay;

@Configuration
@RequiredArgsConstructor
@EnableScheduling
@ConditionalOnProperty(prefix = "app.outbox", name = "relay-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxSchedulingConfig {

	private final OutboxRelay outboxRelay;

	@Scheduled(fixedDelayString = "${app.outbox.poll-interval:1s}")
	public void dispatchPendingMessages() {
		outboxRelay.dispatchPendingMessages();
	}
}
