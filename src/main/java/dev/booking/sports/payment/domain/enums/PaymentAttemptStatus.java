package dev.booking.sports.payment.domain.enums;

public enum PaymentAttemptStatus {
    PREPARED, PENDING, UNKNOWN, SUCCEEDED, FAILED, CANCELLED, EXPIRED;

    public boolean isActive() {
        return this == PREPARED || this == PENDING || this == UNKNOWN;
    }
}
