package dev.booking.sports.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.booking.sports.identity.api.error.AuthErrorCode;
import dev.booking.sports.identity.config.JwtProperties;
import dev.booking.sports.shared.exception.ApiException;

class JwtTokenProviderTest {

	private static final String ACCESS_SECRET = "test-access-token-secret-key-sports-booking-hs256-0123456789";
	private static final String REFRESH_SECRET = "test-refresh-token-secret-key-sports-booking-hs256-9876543210";

	private JwtTokenProvider provider;

	@BeforeEach
	void setUp() {
		provider = new JwtTokenProvider(new JwtProperties(ACCESS_SECRET, REFRESH_SECRET, 3_600_000L, 86_400_000L));
	}

	@Test
	void issueAndParseAccessToken_roundTrip() {
		UUID userId = UUID.randomUUID();
		String jti = UUID.randomUUID().toString();
		Set<String> roles = Set.of("CUSTOMER");
		Set<String> permissions = Set.of("booking.read");

		String token = provider.issueAccessToken(
				userId, "user@example.com", jti, 1L, roles, permissions);

		JwtTokenProvider.ParsedToken parsed = provider.parse(token, TokenType.ACCESS);

		assertThat(parsed.userId()).isEqualTo(userId);
		assertThat(parsed.email()).isEqualTo("user@example.com");
		assertThat(parsed.jti()).isEqualTo(jti);
		assertThat(parsed.tokenVersion()).isEqualTo(1L);
		assertThat(parsed.roles()).containsExactlyInAnyOrder("CUSTOMER");
		assertThat(parsed.permissions()).containsExactlyInAnyOrder("booking.read");
	}

	@Test
	void parseRefreshTokenWithAccessType_throwsTokenInvalid() {
		UUID userId = UUID.randomUUID();
		String jti = UUID.randomUUID().toString();
		String accessToken = provider.issueAccessToken(
				userId, "user@example.com", jti, 1L, Set.of(), Set.of());

		assertThatThrownBy(() -> provider.parse(accessToken, TokenType.REFRESH))
				.isInstanceOf(ApiException.class)
				.extracting(exception -> ((ApiException) exception).getErrorCode())
				.isEqualTo(AuthErrorCode.TOKEN_INVALID);
	}

	@Test
	void parseGarbageToken_throwsTokenInvalid() {
		assertThatThrownBy(() -> provider.parse("not-a-jwt", TokenType.ACCESS))
				.isInstanceOf(ApiException.class)
				.extracting(exception -> ((ApiException) exception).getErrorCode())
				.isEqualTo(AuthErrorCode.TOKEN_INVALID);
	}
}
