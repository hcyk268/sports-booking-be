package dev.booking.sports.booking.domain.enums;

public enum BookingSlotStatus {
    HELD,
    PENDING_PAYMENT,
    CONFIRMED,
    CANCELLED,
    EXPIRED,
    COMPLETED;

    public boolean occupiesCourt() {
        return this == HELD || this == PENDING_PAYMENT || this == CONFIRMED;
    }
}
