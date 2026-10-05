package dev.booking.sports.identity.application;

import java.util.UUID;

import org.springframework.stereotype.Service;

import dev.booking.sports.identity.api.error.AuthErrorCode;
import dev.booking.sports.identity.domain.model.User;
import dev.booking.sports.identity.infrastructure.redis.AccessTokenBlacklist;
import dev.booking.sports.identity.infrastructure.redis.SessionStore;
import dev.booking.sports.identity.infrastructure.redis.TokenVersionStore;
import dev.booking.sports.identity.infrastructure.security.JwtTokenProvider;
import dev.booking.sports.identity.infrastructure.security.TokenHasher;
import dev.booking.sports.identity.infrastructure.security.TokenType;
import dev.booking.sports.shared.exception.ApiException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Service
@RequiredArgsConstructor
public class AuthSessionService {

	private final JwtTokenProvider tokenProvider;
	private final SessionStore sessionStore;
	private final TokenVersionStore tokenVersionStore;
	private final AccessTokenBlacklist accessTokenBlacklist;

	public record IssuedTokens(String accessToken, String refreshToken, long expiresInSeconds) {
	}

	public record RefreshOutcome(UUID userId, String jti, long tokenVersion, String refreshHash) {
	}

	public IssuedTokens startSession(User user, String userAgent, String ip) {
		String jti = UUID.randomUUID().toString();
		long tokenVersion = tokenVersionStore.currentOrCreate(user.getId());

		IssuedTokens tokens = issue(user, jti, tokenVersion);
		sessionStore.create(
				user.getId(),
				jti,
				TokenHasher.sha256Hex(tokens.refreshToken()),
				userAgent,
				ip,
				tokenProvider.refreshTokenTtl());

		return tokens;
	}


	public RefreshOutcome validateRefreshToken(String refreshToken) {
		JwtTokenProvider.ParsedToken parsed = tokenProvider.parse(refreshToken, TokenType.REFRESH);

		if (tokenVersionStore.currentOrCreate(parsed.userId()) != parsed.tokenVersion()) {
			throw new ApiException(AuthErrorCode.TOKEN_REVOKED);
		}

		SessionStore.Session session = sessionStore.find(parsed.userId(), parsed.jti())
				.orElseThrow(() -> new ApiException(AuthErrorCode.TOKEN_REVOKED));

		if (!TokenHasher.sha256Hex(refreshToken).equals(session.refreshHash())) {
			log.warn("Refresh token replay detected for user {}, revoking all sessions", parsed.userId());
			revokeAllSessions(parsed.userId());
			throw new ApiException(AuthErrorCode.REFRESH_TOKEN_REUSED);
		}

		return new RefreshOutcome(
				parsed.userId(),
				parsed.jti(),
				parsed.tokenVersion(),
				session.refreshHash());
	}

	public IssuedTokens rotateSession(
			User user,
			String jti,
			long tokenVersion,
			String expectedRefreshHash) {
		IssuedTokens tokens = issue(user, jti, tokenVersion);
		SessionStore.RotationResult result = sessionStore.rotateRefreshHash(
				user.getId(),
				jti,
				expectedRefreshHash,
				TokenHasher.sha256Hex(tokens.refreshToken()),
				tokenProvider.refreshTokenTtl());
		if (result == SessionStore.RotationResult.SESSION_NOT_FOUND) {
			throw new ApiException(AuthErrorCode.TOKEN_REVOKED);
		}
		if (result == SessionStore.RotationResult.HASH_MISMATCH) {
			log.warn("Concurrent refresh token replay detected for user {}, revoking all sessions", user.getId());
			revokeAllSessions(user.getId());
			throw new ApiException(AuthErrorCode.REFRESH_TOKEN_REUSED);
		}

		return tokens;
	}

	public void endSession(UUID userId, String jti) {
		accessTokenBlacklist.add(userId, jti, tokenProvider.accessTokenTtl());
		sessionStore.delete(userId, jti);
	}

	public void revokeAllSessions(UUID userId) {
		tokenVersionStore.increment(userId);
		sessionStore.deleteAll(userId);
	}

	private IssuedTokens issue(User user, String jti, long tokenVersion) {
		String accessToken = tokenProvider.issueAccessToken(
				user.getId(),
				user.getEmail(),
				jti,
				tokenVersion,
				user.roleCodes(),
				user.permissionCodes());
		String refreshToken = tokenProvider.issueRefreshToken(user.getId(), jti, tokenVersion);

		return new IssuedTokens(accessToken, refreshToken, tokenProvider.accessTokenTtl().toSeconds());
	}
}
