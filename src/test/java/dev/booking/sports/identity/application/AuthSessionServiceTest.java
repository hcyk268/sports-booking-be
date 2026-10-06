package dev.booking.sports.identity.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.booking.sports.identity.api.error.AuthErrorCode;
import dev.booking.sports.identity.config.JwtProperties;
import dev.booking.sports.identity.infrastructure.redis.AccessTokenBlacklist;
import dev.booking.sports.identity.infrastructure.redis.SessionStore;
import dev.booking.sports.identity.infrastructure.redis.TokenVersionStore;
import dev.booking.sports.identity.infrastructure.security.JwtTokenProvider;
import dev.booking.sports.shared.exception.ApiException;

@ExtendWith(MockitoExtension.class)
class AuthSessionServiceTest {

	private static final UUID USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
	private static final String JTI = "session-jti";

	@Mock
	private SessionStore sessionStore;

	@Mock
	private TokenVersionStore tokenVersionStore;

	@Mock
	private AccessTokenBlacklist accessTokenBlacklist;

	private JwtTokenProvider tokenProvider;

	@InjectMocks
	private AuthSessionService authSessionService;

	@BeforeEach
	void setUp() {
		tokenProvider = new JwtTokenProvider(new JwtProperties(
				"test-access-token-secret-key-sports-booking-hs256-0123456789",
				"test-refresh-token-secret-key-sports-booking-hs256-9876543210",
				3_600_000L,
				86_400_000L));
		authSessionService = new AuthSessionService(
				tokenProvider, sessionStore, tokenVersionStore, accessTokenBlacklist);
	}

	@Test
	void validateRefreshToken_hashMismatch_revokesAllSessions() {
		String refreshToken = tokenProvider.issueRefreshToken(USER_ID, JTI, 1L);
		when(tokenVersionStore.currentOrCreate(USER_ID)).thenReturn(1L);
		when(sessionStore.find(USER_ID, JTI))
				.thenReturn(Optional.of(new SessionStore.Session("wrong-hash", null, null, null, null)));

		assertThatThrownBy(() -> authSessionService.validateRefreshToken(refreshToken))
				.isInstanceOf(ApiException.class)
				.extracting(exception -> ((ApiException) exception).getErrorCode())
				.isEqualTo(AuthErrorCode.REFRESH_TOKEN_REUSED);

		verify(sessionStore).deleteAll(USER_ID);
		verify(tokenVersionStore).increment(USER_ID);
	}

	@Test
	void endSession_blacklistsAccessTokenAndDeletesSession() {
		authSessionService.endSession(USER_ID, JTI);

		verify(accessTokenBlacklist).add(eq(USER_ID), eq(JTI), eq(Duration.ofHours(1)));
		verify(sessionStore).delete(USER_ID, JTI);
	}
}
