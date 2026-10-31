package dev.booking.sports.payment.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import dev.booking.sports.booking.publicapi.BookingPaymentSnapshot;
import dev.booking.sports.payment.api.dto.response.PaymentCheckoutResponse;
import dev.booking.sports.payment.api.error.PaymentErrorCode;
import dev.booking.sports.payment.domain.enums.PaymentAttemptStatus;
import dev.booking.sports.payment.domain.enums.PaymentStatus;
import dev.booking.sports.payment.domain.model.Payment;
import dev.booking.sports.payment.domain.model.PaymentAttempt;
import dev.booking.sports.payment.infrastructure.sepay.SePayCheckoutFactory;
import dev.booking.sports.shared.exception.ApiException;

final class PaymentCheckoutSupport {
    static final Set<PaymentAttemptStatus> ACTIVE = Set.of(
            PaymentAttemptStatus.PREPARED, PaymentAttemptStatus.PENDING, PaymentAttemptStatus.UNKNOWN);

    private PaymentCheckoutSupport() {
    }

    static void requirePayableBooking(BookingPaymentSnapshot booking, Instant now) {
        if (!booking.awaitingPayment()) {
            throw new ApiException(PaymentErrorCode.PAYMENT_BOOKING_NOT_PAYABLE);
        }
        if (booking.holdExpiresAt() == null || !now.isBefore(booking.holdExpiresAt())) {
            throw new ApiException(PaymentErrorCode.PAYMENT_EXPIRED);
        }
        if (!"VND".equals(booking.currency())) {
            throw new ApiException(PaymentErrorCode.PAYMENT_CURRENCY_NOT_SUPPORTED);
        }
        if (booking.totalAmount() == null || booking.totalAmount().signum() <= 0
                || booking.totalAmount().stripTrailingZeros().scale() > 0) {
            throw new ApiException(PaymentErrorCode.PAYMENT_AMOUNT_INVALID);
        }
    }

    static void requirePayablePayment(Payment payment, BookingPaymentSnapshot booking, Instant now) {
        if (payment.getStatus() == PaymentStatus.EXPIRED || !now.isBefore(payment.getExpiresAt())) {
            throw new ApiException(PaymentErrorCode.PAYMENT_EXPIRED);
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new ApiException(PaymentErrorCode.PAYMENT_CHECKOUT_NOT_ALLOWED);
        }
        if (payment.getAmount().compareTo(booking.totalAmount()) != 0
                || !payment.getCurrency().equals(booking.currency())
                || !Objects.equals(payment.getExpiresAt(), booking.holdExpiresAt())) {
            throw new ApiException(PaymentErrorCode.PAYMENT_BOOKING_CHANGED);
        }
    }

    static void requireResumable(PaymentAttempt attempt, Instant now) {
        if (attempt.getStatus() == PaymentAttemptStatus.UNKNOWN) {
            throw new ApiException(PaymentErrorCode.PAYMENT_OUTCOME_UNKNOWN);
        }
        if (!attempt.canResumeCheckout(now)) {
            throw new ApiException(PaymentErrorCode.PAYMENT_CHECKOUT_NOT_ALLOWED);
        }
    }

    static PaymentCheckoutResponse toResponse(Payment payment, PaymentAttempt attempt) {
        return new PaymentCheckoutResponse(payment.getId(), attempt.getId(), payment.getStatus(),
                payment.getAmount(), payment.getCurrency(), payment.getExpiresAt(),
                new PaymentCheckoutResponse.CheckoutForm(SePayCheckoutFactory.checkoutAction(attempt.getEnvironment()),
                    "POST", attempt.getCheckoutPayload().stream()
                        .map(field -> new PaymentCheckoutResponse.Field(field.name(), field.value())).toList()));
    }
}
