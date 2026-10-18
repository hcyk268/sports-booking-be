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
            "Price intervals must not overlap within the same day"),

    INVALID_CLOSURE_TIME(
            HttpStatus.BAD_REQUEST,
            "Closure start time must precede end time and times must use whole minutes"),

    COURT_CLOSURE_OVERLAP(
            HttpStatus.CONFLICT,
            "This interval overlaps an existing court closure"),

    COURT_CLOSURE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Court closure does not exist"),

    COURT_IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "Court image does not exist"),
    INVALID_COURT_IMAGE(HttpStatus.BAD_REQUEST, "Upload a valid PNG or JPEG image, at most 6000 pixels per side and 20 million pixels"),
    COURT_IMAGE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Image must not exceed 5 MiB"),
    COURT_IMAGE_LIMIT(HttpStatus.CONFLICT, "A court can have at most 10 images"),
    COURT_IMAGE_STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Image storage is temporarily unavailable"),

    INVALID_COURT_SEARCH_FILTER(
            HttpStatus.BAD_REQUEST,
            "Date is required for time or price filters; provide both startTime and endTime in whole minutes, start before end, and a non-negative ordered price range");

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
