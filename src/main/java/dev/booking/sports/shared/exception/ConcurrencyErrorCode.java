package dev.booking.sports.shared.exception;

import org.springframework.http.HttpStatus;

public enum ConcurrencyErrorCode implements ErrorCode {

    OPTIMISTIC_LOCK_CONFLICT(
            HttpStatus.CONFLICT,
            "Resource was modified by another request; reload and retry");

    private final HttpStatus status;
    private final String message;

    ConcurrencyErrorCode(HttpStatus status, String message) {
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
