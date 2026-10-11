package dev.booking.sports.court.api.error;

import org.springframework.http.HttpStatus;

import dev.booking.sports.shared.exception.ErrorCode;

public enum CourtErrorCode implements ErrorCode {

    SPORT_NOT_FOUND(HttpStatus.BAD_REQUEST, "Sport does not exist"),
    COURT_NOT_FOUND(HttpStatus.NOT_FOUND, "Court does not exist"),
    COURT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "You do not own this court");

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