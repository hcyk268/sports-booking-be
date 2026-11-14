package dev.booking.sports.notification.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.*;
import dev.booking.sports.identity.config.JwtProperties;
import dev.booking.sports.identity.infrastructure.redis.AuthRedisKeys;
import dev.booking.sports.identity.infrastructure.security.JwtTokenProvider;
import dev.booking.sports.support.*;

@IntegrationTest
@AutoConfigureMockMvc
class NotificationStreamIntegrationTest extends BaseIntegrationTest {
    private static final String PATH = "/api/v1/notifications/stream";
    @Autowired MockMvc mvc;
    @Autowired StringRedisTemplate redis;
    @Autowired JwtTokenProvider tokens;
    @Autowired JwtProperties jwtProperties;
    @Autowired NotificationSseRegistry registry;
    private UUID user;
    private String jti;
    private final List<MvcResult> streams = new ArrayList<>();

    @BeforeEach void setup() {
        user = UUID.randomUUID(); jti = UUID.randomUUID().toString();
        redis.opsForValue().set(AuthRedisKeys.tokenVersion(user), "1", Duration.ofMinutes(5));
    }

    @AfterEach void cleanup() throws Exception {
        redis.opsForValue().set(AuthRedisKeys.accessTokenBlacklist(user, jti), "1", Duration.ofMinutes(5));
        registry.publish(new NotificationChangedEvent(UUID.randomUUID(), user, NotificationChangedEvent.Action.READ_ALL, null));
        for (var stream : streams) {
            stream.getAsyncResult(10_000);
            mvc.perform(asyncDispatch(stream)).andExpect(status().isOk());
        }
        redis.delete(List.of(AuthRedisKeys.tokenVersion(user), AuthRedisKeys.accessTokenBlacklist(user, jti)));
    }

    private String accessToken() {
        return tokens.issueAccessToken(user, "test@example.com", jti, 1, Set.of("CUSTOMER"), Set.of());
    }
    private MvcResult open(String token) throws Exception {
        var result = mvc.perform(get(PATH).accept("text/event-stream").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(request().asyncStarted())
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Accel-Buffering", "no")).andReturn();
        streams.add(result);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(result.getResponse().getContentAsString()).contains("event:ready", "connectionId"));
        return result;
    }

    @Test void requiresValidAccessTokenAndRejectsRevokedOrRefreshTokens() throws Exception {
        mvc.perform(get(PATH).accept("text/event-stream")).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
        mvc.perform(get(PATH).param("access_token", accessToken())).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer invalid")).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.issueRefreshToken(user, jti, 1)))
                .andExpect(status().isUnauthorized());
        redis.opsForValue().set(AuthRedisKeys.accessTokenBlacklist(user, jti), "1", Duration.ofMinutes(5));
        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())).andExpect(status().isUnauthorized());
    }

    @Test void serializesLocalEventsForAllOwnedStreamsAndClosesAfterRevocation() throws Exception {
        var first = open(accessToken());
        var second = open(accessToken());
        var foreign = new NotificationChangedEvent(UUID.randomUUID(), UUID.randomUUID(), NotificationChangedEvent.Action.CREATED, UUID.randomUUID());
        registry.publish(foreign);
        var own = new NotificationChangedEvent(UUID.randomUUID(), user, NotificationChangedEvent.Action.CREATED, UUID.randomUUID());
        registry.publish(own);
        for (var stream : List.of(first, second)) {
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(stream.getResponse().getContentAsString()).contains("event:notification.changed", own.notificationId().toString()));
            assertThat(stream.getResponse().getContentAsString()).doesNotContain(foreign.eventId().toString(), "recipientId");
        }
        redis.opsForValue().set(AuthRedisKeys.accessTokenBlacklist(user, jti), "1", Duration.ofMinutes(5));
        registry.publish(own);
        first.getAsyncResult(5_000);
        second.getAsyncResult(5_000);
    }

    @Test void enforcesPerUserLimitWithStandard429Response() throws Exception {
        String token = accessToken();
        for (int i = 0; i < 5; i++) open(token);
        mvc.perform(get(PATH).accept("text/event-stream").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_STREAM_LIMIT"));
    }

    @Test void automaticallyCompletesStreamAtJwtExpiry() throws Exception {
        var shortTokens = new JwtTokenProvider(new JwtProperties(jwtProperties.secretKey(),
                jwtProperties.refreshSecretKey(), 2500L, jwtProperties.refreshExpirationMs()));
        var stream = open(shortTokens.issueAccessToken(user, "test@example.com", jti, 1, Set.of("CUSTOMER"), Set.of()));
        stream.getAsyncResult(7_000);
        assertThat(registry.connectionCount()).isZero();
    }
}
