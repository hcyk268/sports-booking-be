package dev.booking.sports.payment.domain.enums;

public enum PaymentRefundStatus {
    REQUESTED, PROCESSING, COMPLETED, REJECTED, CANCELLED;

    public boolean reservesFunds() {
        return this == REQUESTED || this == PROCESSING || this == COMPLETED;
    }
}
