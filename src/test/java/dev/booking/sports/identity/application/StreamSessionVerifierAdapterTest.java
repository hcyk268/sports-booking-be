package dev.booking.sports.identity.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import dev.booking.sports.identity.config.JwtProperties;
import dev.booking.sports.identity.infrastructure.security.JwtTokenProvider;
import dev.booking.sports.identity.infrastructure.redis.AuthStateStore;
import dev.booking.sports.identity.publicapi.StreamSession;
import dev.booking.sports.shared.exception.ApiException;

class StreamSessionVerifierAdapterTest {
    private final AuthStateStore states = mock(AuthStateStore.class);
    private final JwtTokenProvider tokens = new JwtTokenProvider(new JwtProperties(
            "test-access-token-secret-key-sports-booking-hs256-0123456789",
            "test-refresh-token-secret-key-sports-booking-hs256-9876543210", 900000L, 86400000L));
    private final StreamSessionVerifierAdapter verifier = new StreamSessionVerifierAdapter(tokens, states);

    @Test void verifiesTokenAndTracksExpiryAndRevocationWithoutRetainingBearer() {
        UUID user = UUID.randomUUID();
        String token = tokens.issueAccessToken(user, "test@example.com", "jti", 2, Set.of("CUSTOMER"), Set.of());
        when(states.read(user, "jti")).thenReturn(new AuthStateStore.AuthState(2, true, false));
        var session = verifier.authenticate("Bearer " + token);
        assertThat(session.userId()).isEqualTo(user);
        assertThat(session.expiresAt()).isAfter(Instant.now()).isBefore(Instant.now().plusSeconds(901));
        assertThat(verifier.isActive(session)).isTrue();
        when(states.read(user, "jti")).thenReturn(new AuthStateStore.AuthState(2, true, true));
        assertThat(verifier.isActive(session)).isFalse();
        when(states.read(user, "jti")).thenReturn(new AuthStateStore.AuthState(3, true, false));
        assertThat(verifier.isActive(session)).isFalse();
        when(states.read(user, "jti")).thenReturn(new AuthStateStore.AuthState(0, false, false));
        assertThat(verifier.isActive(session)).isFalse();
    }

    @Test void rejectsMissingInvalidRefreshAndExpiredCredentials() {
        UUID user = UUID.randomUUID();
        assertThatThrownBy(() -> verifier.authenticate(null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> verifier.authenticate("Bearer invalid")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> verifier.authenticate("Bearer " + tokens.issueRefreshToken(user, "jti", 1)))
                .isInstanceOf(ApiException.class);
        assertThat(verifier.isActive(new StreamSession(user, "jti", 1, Instant.EPOCH))).isFalse();
        verifyNoInteractions(states);
    }
}
