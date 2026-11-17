package dev.booking.sports.review;

import org.springframework.http.HttpStatus;
import dev.booking.sports.shared.exception.ErrorCode;

enum ReviewErrors implements ErrorCode {
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "Review or court not found"),
    BOOKING_NOT_REVIEWABLE(HttpStatus.CONFLICT, "A completed booking at this court is required"),
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "This booking already has a review"),
    REVIEW_CONFLICT(HttpStatus.CONFLICT, "Review changed; reload before editing");

    private final HttpStatus status;
    private final String message;

    ReviewErrors(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
    public String code() { return name(); }
    public HttpStatus status() { return status; }
    public String defaultMessage() { return message; }
}
