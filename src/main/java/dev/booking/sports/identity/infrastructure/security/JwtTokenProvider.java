package dev.booking.sports.identity.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import dev.booking.sports.identity.api.error.AuthErrorCode;
import dev.booking.sports.identity.config.JwtProperties;
import dev.booking.sports.shared.exception.ApiException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtTokenProvider {

	private static final String CLAIM_TOKEN_VERSION = "tv";
	private static final String CLAIM_TOKEN_TYPE = "typ";
	private static final String CLAIM_ROLES = "roles";
	private static final String CLAIM_PERMISSIONS = "permissions";
	private static final String CLAIM_EMAIL = "email";
	private static final String CLAIM_NONCE = "nonce";

	private final SecretKey accessKey;
	private final SecretKey refreshKey;
	private final Duration accessTtl;
	private final Duration refreshTtl;

	public JwtTokenProvider(JwtProperties properties) {
		this.accessKey = Keys.hmacShaKeyFor(properties.secretKey().getBytes(StandardCharsets.UTF_8));
		this.refreshKey = Keys.hmacShaKeyFor(properties.refreshSecretKey().getBytes(StandardCharsets.UTF_8));
		this.accessTtl = properties.accessTokenTtl();
		this.refreshTtl = properties.refreshTokenTtl();
	}

	public record ParsedToken(
			UUID userId,
			String email,
			String jti,
			long tokenVersion,
			Set<String> roles,
			Set<String> permissions) {
	}

	public Duration accessTokenTtl() {
		return accessTtl;
	}

	public Duration refreshTokenTtl() {
		return refreshTtl;
	}

	public String issueAccessToken(
			UUID userId,
			String email,
			String jti,
			long tokenVersion,
			Set<String> roles,
			Set<String> permissions) {
		Instant now = Instant.now();

		return Jwts.builder()
				.subject(userId.toString())
				.id(jti)
				.claim(CLAIM_TOKEN_TYPE, TokenType.ACCESS.claimValue())
				.claim(CLAIM_TOKEN_VERSION, tokenVersion)
				.claim(CLAIM_EMAIL, email)
				.claim(CLAIM_ROLES, List.copyOf(roles))
				.claim(CLAIM_PERMISSIONS, List.copyOf(permissions))
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plus(accessTtl)))
				.signWith(accessKey, Jwts.SIG.HS256)
				.compact();
	}

	public String issueRefreshToken(UUID userId, String jti, long tokenVersion) {
		Instant now = Instant.now();

		return Jwts.builder()
				.subject(userId.toString())
				.id(jti)
				.claim(CLAIM_TOKEN_TYPE, TokenType.REFRESH.claimValue())
				.claim(CLAIM_TOKEN_VERSION, tokenVersion)
				.claim(CLAIM_NONCE, UUID.randomUUID().toString())
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plus(refreshTtl)))
				.signWith(refreshKey, Jwts.SIG.HS256)
				.compact();
	}

	public ParsedToken parse(String token, TokenType expectedType) {
		SecretKey key = expectedType == TokenType.ACCESS ? accessKey : refreshKey;

		Claims claims;
		try {
			claims = Jwts.parser()
					.verifyWith(key)
					.build()
					.parseSignedClaims(token)
					.getPayload();
		}
		catch (JwtException | IllegalArgumentException exception) {
			throw new ApiException(AuthErrorCode.TOKEN_INVALID);
		}

		if (!expectedType.claimValue().equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
			throw new ApiException(AuthErrorCode.TOKEN_INVALID);
		}

		return new ParsedToken(
				parseUserId(claims.getSubject()),
				claims.get(CLAIM_EMAIL, String.class),
				claims.getId(),
				tokenVersionOf(claims),
				stringSetClaimOf(claims, CLAIM_ROLES),
				stringSetClaimOf(claims, CLAIM_PERMISSIONS));
	}

	private UUID parseUserId(String subject) {
		try {
			return UUID.fromString(subject);
		}
		catch (IllegalArgumentException | NullPointerException exception) {
			throw new ApiException(AuthErrorCode.TOKEN_INVALID);
		}
	}

	private long tokenVersionOf(Claims claims) {
		Number tokenVersion = claims.get(CLAIM_TOKEN_VERSION, Number.class);
		if (tokenVersion == null) {
			throw new ApiException(AuthErrorCode.TOKEN_INVALID);
		}
		return tokenVersion.longValue();
	}

	private Set<String> stringSetClaimOf(Claims claims, String claimName) {
		Object claim = claims.get(claimName);
		if (claim instanceof Collection<?> values) {
			return values.stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet());
		}
		return Set.of();
	}
}
