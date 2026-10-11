package dev.booking.sports.identity.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.booking.sports.identity.infrastructure.security.TokenHasher;
import dev.booking.sports.support.BaseIntegrationTest;
import dev.booking.sports.support.IntegrationTest;

@IntegrationTest
class EmailVerificationTokenStoreIntegrationTest extends BaseIntegrationTest {

	@Autowired
	private EmailVerificationTokenStore tokenStore;

	@Test
	void consume_isOneTime() {
		UUID userId = UUID.fromString("55555555-5555-5555-5555-555555555555");
		String tokenHash = TokenHasher.sha256Hex("integration-verification-token");

		tokenStore.issue(tokenHash, userId, Duration.ofMinutes(15));

		assertThat(tokenStore.consume(tokenHash)).contains(userId);
		assertThat(tokenStore.consume(tokenHash)).isEmpty();
	}
}
