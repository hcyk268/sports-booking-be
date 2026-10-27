package dev.booking.sports.booking.domain.event;

public final class BookingOutboxMessageTypes {

    public static final String BOOKING_AGGREGATE_TYPE = "BOOKING";
    public static final int EVENT_VERSION = 1;

    public static final String BOOKING_HELD = "booking.held";
    public static final String BOOKING_CONFIRMED = "booking.confirmed";
    public static final String BOOKING_CANCELLED = "booking.cancelled";
    public static final String BOOKING_EXPIRED = "booking.expired";

    private BookingOutboxMessageTypes() {
    }
}
