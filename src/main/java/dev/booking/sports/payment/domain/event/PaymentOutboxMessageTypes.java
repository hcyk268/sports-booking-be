package dev.booking.sports.payment.domain.event;

public final class PaymentOutboxMessageTypes {

    public static final String SUCCEEDED = "payment.succeeded";
    public static final String REVIEW_REQUIRED = "payment.review_required";
    public static final int VERSION = 1;

    private PaymentOutboxMessageTypes() {}
}
