package dev.booking.sports.booking.api.error;

import org.springframework.http.HttpStatus;

import dev.booking.sports.shared.exception.ErrorCode;

public enum BookingErrorCode implements ErrorCode {

    BOOKING_NOT_FOUND(HttpStatus.NOT_FOUND, "Booking does not exist"),

    BOOKING_ACCESS_DENIED(HttpStatus.FORBIDDEN, "You cannot manage this booking"),

    INVALID_BOOKING_SLOTS(HttpStatus.BAD_REQUEST, "Booking slots are invalid"),

    BOOKING_SLOT_NOT_BOOKABLE(HttpStatus.CONFLICT, "One or more selected slots are not bookable"),

    BOOKING_SLOT_UNAVAILABLE(HttpStatus.CONFLICT, "One or more selected slots are no longer available"),

    BOOKING_CANCELLATION_NOT_ALLOWED(HttpStatus.CONFLICT, "Booking cannot be cancelled in its current state"),

    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT,"Idempotency key was already used with a different request"),

    IDEMPOTENCY_REQUEST_IN_PROGRESS(HttpStatus.CONFLICT, "A booking request with this idempotency key is already being processed"),

    INVALID_BOOKING_PAGINATION(HttpStatus.BAD_REQUEST, "Page must be non-negative and size must be between 1 and 100");

    private final HttpStatus status;
    private final String message;

    BookingErrorCode(HttpStatus status, String message) {
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
