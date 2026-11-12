package dev.booking.sports.notification.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import dev.booking.sports.identity.domain.model.User;
import dev.booking.sports.identity.domain.repository.UserRepository;
import dev.booking.sports.notification.domain.model.Notification;
import dev.booking.sports.support.BaseIntegrationTest;
import dev.booking.sports.support.IntegrationTest;
import jakarta.persistence.EntityManager;

@IntegrationTest
@Transactional
class NotificationRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired NotificationRepository notifications;
    @Autowired UserRepository users;
    @Autowired EntityManager entityManager;

    @Test
    void persistsReadStateAndScopesHistoryAndUnreadCountToRecipient() {
        UUID recipientId = recipient();
        UUID otherRecipientId = recipient();
        Notification notification = notifications.saveAndFlush(notification(recipientId, UUID.randomUUID(), "BOOKING_CONFIRMED"));
        notifications.saveAndFlush(notification(otherRecipientId, UUID.randomUUID(), "BOOKING_CONFIRMED"));
        UUID notificationId = notification.getId();
        Instant readAt = Instant.parse("2026-11-12T00:00:00Z");

        assertThat(notification.getCreatedAt()).isNotNull();
        assertThat(notifications.countByRecipientIdAndReadAtIsNull(recipientId)).isEqualTo(1);
        assertThat(notifications.findByIdAndRecipientId(notificationId, otherRecipientId)).isEmpty();
        assertThat(notifications.findByRecipientIdOrderByCreatedAtDescIdDesc(recipientId, PageRequest.of(0, 20)))
                .extracting(Notification::getId).containsExactly(notificationId);

        notification.markRead(readAt);
        notification.markRead(readAt.plusSeconds(60));
        notifications.flush();
        entityManager.clear();

        assertThat(notifications.findByIdAndRecipientId(notificationId, recipientId).orElseThrow().getReadAt())
                .isEqualTo(readAt);
        assertThat(notifications.countByRecipientIdAndReadAtIsNull(recipientId)).isZero();
        assertThat(notifications.countByRecipientIdAndReadAtIsNull(otherRecipientId)).isEqualTo(1);
    }

    @Test
    void rejectsDuplicateSourceEventForSameRecipientAndType() {
        UUID recipientId = recipient();
        UUID eventId = UUID.randomUUID();
        notifications.saveAndFlush(notification(recipientId, eventId, "BOOKING_CONFIRMED"));

        assertThatThrownBy(() -> notifications.saveAndFlush(notification(recipientId, eventId, "BOOKING_CONFIRMED")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsOneEventToNotifyDifferentRecipientsAndNotificationTypes() {
        UUID recipientId = recipient();
        UUID otherRecipientId = recipient();
        UUID eventId = UUID.randomUUID();
        notifications.saveAndFlush(notification(recipientId, eventId, "BOOKING_CONFIRMED"));
        notifications.saveAndFlush(notification(otherRecipientId, eventId, "BOOKING_CONFIRMED"));
        notifications.saveAndFlush(notification(recipientId, eventId, "NEW_BOOKING"));

        assertThat(notifications.countByRecipientIdAndReadAtIsNull(recipientId)).isEqualTo(2);
        assertThat(notifications.countByRecipientIdAndReadAtIsNull(otherRecipientId)).isEqualTo(1);
    }

    private UUID recipient() {
        return users.saveAndFlush(User.register(UUID.randomUUID() + "@example.com", "encoded-hash", "Test User", null))
                .getId();
    }

    private Notification notification(UUID recipientId, UUID eventId, String type) {
        return Notification.create(recipientId, type, "Booking confirmed", "Your court booking is confirmed.",
                "BOOKING", UUID.randomUUID(), eventId);
    }
}
