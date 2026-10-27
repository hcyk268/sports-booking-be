package dev.booking.sports.booking.domain.enums;

public enum BookingStatus {
    HELD,
    PENDING_PAYMENT,
    CONFIRMED,
    CANCELLED,
    EXPIRED,
    COMPLETED;

    public boolean isTerminal() {
        return this == CANCELLED || this == EXPIRED || this == COMPLETED;
    }
}
