package dev.booking.sports.notification.api.error;

import org.springframework.http.HttpStatus;

import dev.booking.sports.shared.exception.ErrorCode;

public enum NotificationErrorCode implements ErrorCode {
    NOTIFICATION_STREAM_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "Notification stream connection limit reached"),
    NOTIFICATION_STREAM_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Notification stream is unavailable"),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Notification does not exist"),
    INVALID_NOTIFICATION_PAGINATION(HttpStatus.BAD_REQUEST,
            "Page must be non-negative and size must be between 1 and 100");

    private final HttpStatus status;
    private final String message;

    NotificationErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String defaultMessage() {
        return message;
    }
}
