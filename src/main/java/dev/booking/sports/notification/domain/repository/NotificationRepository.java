package dev.booking.sports.notification.domain.repository;

import java.util.Optional;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import dev.booking.sports.notification.domain.model.Notification;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipientIdOrderByCreatedAtDescIdDesc(UUID recipientId, Pageable pageable);

    Optional<Notification> findByIdAndRecipientId(UUID id, UUID recipientId);

    long countByRecipientIdAndReadAtIsNull(UUID recipientId);

    @Modifying
    @Query(value = """
            INSERT INTO notifications (id, recipient_id, type, title, body, resource_type, resource_id, source_event_id)
            VALUES (:id, :recipientId, :type, :title, :body, :resourceType, :resourceId, :sourceEventId)
            ON CONFLICT ON CONSTRAINT uk_notifications_source_recipient_type DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("recipientId") UUID recipientId,
            @Param("type") String type, @Param("title") String title, @Param("body") String body,
            @Param("resourceType") String resourceType, @Param("resourceId") UUID resourceId,
            @Param("sourceEventId") UUID sourceEventId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Notification n SET n.readAt = :readAt
            WHERE n.id = :id AND n.recipientId = :recipientId AND n.readAt IS NULL
            """)
    int markRead(@Param("id") UUID id, @Param("recipientId") UUID recipientId, @Param("readAt") Instant readAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Notification n SET n.readAt = :readAt
            WHERE n.recipientId = :recipientId AND n.readAt IS NULL
            """)
    int markAllRead(@Param("recipientId") UUID recipientId, @Param("readAt") Instant readAt);
}
