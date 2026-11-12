package dev.booking.sports.notification.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.booking.sports.notification.api.dto.response.NotificationResponse;
import dev.booking.sports.notification.api.dto.response.UnreadCountResponse;
import dev.booking.sports.notification.api.error.NotificationErrorCode;
import dev.booking.sports.notification.domain.repository.NotificationRepository;
import dev.booking.sports.shared.api.PageResponse;
import dev.booking.sports.shared.exception.ApiException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository repository;

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(UUID recipientId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(NotificationErrorCode.INVALID_NOTIFICATION_PAGINATION);
        }
        var result = repository.findByRecipientIdOrderByCreatedAtDescIdDesc(recipientId, PageRequest.of(page, size));
        return PageResponse.of(result.getContent().stream().map(NotificationResponse::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(UUID recipientId) {
        return new UnreadCountResponse(repository.countByRecipientIdAndReadAtIsNull(recipientId));
    }

    @Transactional
    public NotificationResponse markRead(UUID recipientId, UUID notificationId) {
        repository.markRead(notificationId, recipientId, Instant.now());
        return repository.findByIdAndRecipientId(notificationId, recipientId)
                .map(NotificationResponse::from)
                .orElseThrow(() -> new ApiException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
    }

    @Transactional
    public void markAllRead(UUID recipientId) {
        repository.markAllRead(recipientId, Instant.now());
    }
}
