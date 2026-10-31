package dev.booking.sports.booking.publicapi;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BookingPaymentSnapshot(UUID bookingId, String bookingCode, UUID customerId,
        BigDecimal totalAmount, String currency, Instant holdExpiresAt, boolean awaitingPayment) {
}
