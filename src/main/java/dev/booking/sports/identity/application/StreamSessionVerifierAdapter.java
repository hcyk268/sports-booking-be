package dev.booking.sports.identity.application;

import java.time.Instant;
import org.springframework.stereotype.Service;
import dev.booking.sports.identity.api.error.AuthErrorCode;
import dev.booking.sports.identity.infrastructure.redis.AuthStateStore;
import dev.booking.sports.identity.infrastructure.security.JwtTokenProvider;
import dev.booking.sports.identity.infrastructure.security.TokenType;
import dev.booking.sports.identity.publicapi.StreamSession;
import dev.booking.sports.identity.publicapi.StreamSessionVerifier;
import dev.booking.sports.shared.exception.ApiException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StreamSessionVerifierAdapter implements StreamSessionVerifier {
    private final JwtTokenProvider tokens;
    private final AuthStateStore states;

    @Override
    public StreamSession authenticate(String header) {
        if (header == null || !header.startsWith("Bearer ") || header.substring(7).isBlank()) {
            throw new ApiException(AuthErrorCode.AUTHENTICATION_REQUIRED);
        }
        var token = tokens.parse(header.substring(7), TokenType.ACCESS);
        var session = new StreamSession(token.userId(), token.jti(), token.tokenVersion(), token.expiresAt());
        if (!isActive(session)) throw new ApiException(AuthErrorCode.TOKEN_REVOKED);
        return session;
    }

    @Override
    public boolean isActive(StreamSession session) {
        if (!Instant.now().isBefore(session.expiresAt())) return false;
        var state = states.read(session.userId(), session.jti());
        return state.tokenVersionExists() && !state.accessTokenBlacklisted()
                && state.tokenVersion() == session.tokenVersion();
    }
}
