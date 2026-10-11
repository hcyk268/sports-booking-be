package dev.booking.sports.court.api.error;

import org.springframework.http.HttpStatus;

import dev.booking.sports.shared.exception.ErrorCode;

public enum CourtErrorCode implements ErrorCode {

    SPORT_NOT_FOUND(HttpStatus.BAD_REQUEST, "Sport does not exist");

    private final HttpStatus status;
    private final String message;

    CourtErrorCode(HttpStatus status, String message) {
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