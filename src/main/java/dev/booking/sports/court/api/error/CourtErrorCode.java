package dev.booking.sports.court.api.error;

import org.springframework.http.HttpStatus;

import dev.booking.sports.shared.exception.ErrorCode;

public enum CourtErrorCode implements ErrorCode {

    SPORT_NOT_FOUND(
            HttpStatus.BAD_REQUEST,
            "Sport does not exist"),

    COURT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Court does not exist"),

    COURT_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "You do not own this court"),

    INVALID_PAGINATION(
            HttpStatus.BAD_REQUEST,
            "Page must be non-negative and size must be between 1 and 100"),

    COURT_PUBLISH_NOT_ALLOWED(
            HttpStatus.CONFLICT,
            "Only draft courts can be published"),

    DUPLICATE_OPERATING_DAY(
            HttpStatus.BAD_REQUEST,
            "Each day can appear only once"),

    INVALID_OPERATING_HOURS(
            HttpStatus.BAD_REQUEST,
            "Opening time must precede closing time, times must use whole minutes, and at least one slot must fit"),

    INVALID_PRICE_RULE_TIME(
            HttpStatus.BAD_REQUEST,
            "Start time must precede end time and times must use whole minutes"),

    PRICE_RULE_OVERLAP(
            HttpStatus.BAD_REQUEST,
            "Price intervals must not overlap within the same day");

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