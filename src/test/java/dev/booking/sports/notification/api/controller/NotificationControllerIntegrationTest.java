package dev.booking.sports.notification.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import dev.booking.sports.identity.infrastructure.security.AuthPrincipal;
import dev.booking.sports.notification.domain.model.Notification;
import dev.booking.sports.notification.domain.repository.NotificationRepository;
import dev.booking.sports.support.BaseIntegrationTest;
import dev.booking.sports.support.IntegrationTest;
import jakarta.persistence.EntityManager;

@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class NotificationControllerIntegrationTest extends BaseIntegrationTest {

    private static final String BASE = "/api/v1/notifications";

    @Autowired MockMvc mvc;
    @Autowired NotificationRepository notifications;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    @Test
    void allEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/unread-count")).andExpect(status().isUnauthorized());
        mvc.perform(patch(BASE + "/" + UUID.randomUUID() + "/read")).andExpect(status().isUnauthorized());
        mvc.perform(patch(BASE + "/read-all")).andExpect(status().isUnauthorized());
    }

    @Test
    void listUsesCurrentRecipientAndStableNewestFirstPagination() throws Exception {
        UUID recipient = recipient();
        UUID other = recipient();
        Notification older = notification(recipient);
        Notification first = notification(recipient);
        Notification second = notification(recipient);
        notification(other);
        Instant time = Instant.parse("2026-11-12T00:00:00Z");
        jdbc.update("UPDATE notifications SET created_at = ? WHERE id = ?", Timestamp.from(time), older.getId());
        jdbc.update("UPDATE notifications SET created_at = ? WHERE id IN (?, ?)",
                Timestamp.from(time.plusSeconds(60)), first.getId(), second.getId());
        entityManager.clear();
        List<String> newest = List.of(first.getId().toString(), second.getId().toString()).stream()
                .sorted(Comparator.reverseOrder()).toList();

        mvc.perform(get(BASE).with(authentication(auth(recipient, "CUSTOMER")))
                        .param("size", "1").param("recipientId", other.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(newest.getFirst()))
                .andExpect(jsonPath("$.data.items[0].recipientId").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].sourceEventId").doesNotExist())
                .andExpect(jsonPath("$.data.pagination.page").value(0))
                .andExpect(jsonPath("$.data.pagination.size").value(1))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(3))
                .andExpect(jsonPath("$.data.pagination.hasNext").value(true));
        mvc.perform(get(BASE).with(authentication(auth(recipient, "CUSTOMER"))).param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(newest.get(1)));
        mvc.perform(get(BASE).with(authentication(auth(recipient, "CUSTOMER"))).param("page", "2").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(older.getId().toString()))
                .andExpect(jsonPath("$.data.pagination.hasNext").value(false));
        mvc.perform(get(BASE).with(authentication(auth(recipient, "CUSTOMER"))).param("page", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.pagination.totalElements").value(3));
    }

    @ParameterizedTest
    @ValueSource(strings = { "CUSTOMER", "PROVIDER", "ADMIN" })
    void unreadCountOnlyIncludesCurrentUsersUnreadNotifications(String role) throws Exception {
        UUID recipient = recipient();
        notification(recipient);
        Notification read = notification(recipient);
        read.markRead(Instant.now());
        notifications.flush();
        notification(recipient());

        mvc.perform(get(BASE + "/unread-count").with(authentication(auth(recipient, role))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1));
    }

    @Test
    void markingReadIsPersistentAndIdempotent() throws Exception {
        UUID recipient = recipient();
        Notification notification = notification(recipient);
        UUID id = notification.getId();
        String path = BASE + "/" + id + "/read";

        mvc.perform(patch(path).with(authentication(auth(recipient, "CUSTOMER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id.toString()))
                .andExpect(jsonPath("$.data.readAt").isNotEmpty());
        entityManager.clear();
        Instant firstRead = notifications.findById(id).orElseThrow().getReadAt();
        assertThat(firstRead).isNotNull();
        mvc.perform(patch(path).with(authentication(auth(recipient, "CUSTOMER"))))
                .andExpect(status().isOk());
        entityManager.clear();
        assertThat(notifications.findById(id).orElseThrow().getReadAt()).isEqualTo(firstRead);
        assertThat(notifications.countByRecipientIdAndReadAtIsNull(recipient)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = { "CUSTOMER", "ADMIN" })
    void missingAndOtherUsersNotificationsReturnSame404WithoutMutation(String role) throws Exception {
        UUID recipient = recipient();
        Notification other = notification(recipient());
        for (UUID id : List.of(other.getId(), UUID.randomUUID())) {
            mvc.perform(patch(BASE + "/" + id + "/read").with(authentication(auth(recipient, role))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
        }
        entityManager.clear();
        assertThat(notifications.findById(other.getId()).orElseThrow().getReadAt()).isNull();
    }

    @Test
    void readAllOnlyUpdatesOwnedUnreadRowsAndPreservesFirstReadTime() throws Exception {
        UUID recipient = recipient();
        Notification unread = notification(recipient);
        Notification read = notification(recipient);
        Instant originalRead = Instant.parse("2026-11-12T00:00:00Z");
        read.markRead(originalRead);
        notifications.flush();
        Notification other = notification(recipient());

        mvc.perform(patch(BASE + "/read-all").with(authentication(auth(recipient, "PROVIDER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
        entityManager.clear();
        Instant firstRead = notifications.findById(unread.getId()).orElseThrow().getReadAt();
        assertThat(firstRead).isNotNull();
        assertThat(notifications.findById(read.getId()).orElseThrow().getReadAt()).isEqualTo(originalRead);
        assertThat(notifications.findById(other.getId()).orElseThrow().getReadAt()).isNull();
        mvc.perform(patch(BASE + "/read-all").with(authentication(auth(recipient, "PROVIDER"))))
                .andExpect(status().isOk());
        entityManager.clear();
        assertThat(notifications.findById(unread.getId()).orElseThrow().getReadAt()).isEqualTo(firstRead);
        assertThat(notifications.countByRecipientIdAndReadAtIsNull(recipient)).isZero();
        Notification arrivedLater = notification(recipient);
        assertThat(arrivedLater.getReadAt()).isNull();
        assertThat(notifications.countByRecipientIdAndReadAtIsNull(recipient)).isEqualTo(1);
    }

    @Test
    void emptyInboxReturnsEmptyPageZeroCountAndReadAllSuccess() throws Exception {
        UUID recipient = recipient();
        mvc.perform(get(BASE).with(authentication(auth(recipient, "CUSTOMER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.pagination.size").value(20))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(0));
        mvc.perform(get(BASE + "/unread-count").with(authentication(auth(recipient, "CUSTOMER"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.unreadCount").value(0));
        mvc.perform(patch(BASE + "/read-all").with(authentication(auth(recipient, "CUSTOMER"))))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @CsvSource({ "-1,20", "0,0", "0,101" })
    void rejectsInvalidPagination(int page, int size) throws Exception {
        mvc.perform(get(BASE).with(authentication(auth(recipient(), "CUSTOMER")))
                        .param("page", Integer.toString(page)).param("size", Integer.toString(size)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_NOTIFICATION_PAGINATION"));
    }

    @Test
    void rejectsMalformedNotificationIdAndPage() throws Exception {
        var auth = authentication(auth(recipient(), "CUSTOMER"));
        mvc.perform(patch(BASE + "/invalid-id/read").with(auth))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        mvc.perform(get(BASE).with(auth).param("page", "invalid-page"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    private UUID recipient() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, password_hash, full_name, status) VALUES (?, ?, ?, ?, ?)",
                id, id + "@example.com", "unused-test-hash", "Notification Test", "ACTIVE");
        return id;
    }

    private Notification notification(UUID recipientId) {
        return notifications.saveAndFlush(Notification.create(recipientId, "BOOKING_CONFIRMED", "Booking confirmed",
                "Your court booking is confirmed.", "BOOKING", UUID.randomUUID(), UUID.randomUUID()));
    }

    private UsernamePasswordAuthenticationToken auth(UUID recipientId, String role) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthPrincipal(recipientId, "test@example.com", "test-jti", Set.of(role), Set.of()),
                null, AuthorityUtils.createAuthorityList("ROLE_" + role));
    }
}
