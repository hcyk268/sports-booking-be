package dev.booking.sports.payment.application;

import dev.booking.sports.booking.publicapi.BookingPaymentFacade;
import dev.booking.sports.booking.publicapi.BookingPaymentSnapshot;
import dev.booking.sports.payment.api.dto.response.PaymentCheckoutResponse;
import dev.booking.sports.payment.api.error.PaymentErrorCode;
import dev.booking.sports.payment.domain.enums.PaymentStatus;
import dev.booking.sports.payment.domain.repository.PaymentAttemptRepository;
import dev.booking.sports.payment.domain.repository.PaymentRepository;
import dev.booking.sports.shared.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static dev.booking.sports.payment.application.PaymentCheckoutSupport.*;

@RequiredArgsConstructor
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingPaymentFacade bookingFacade;
    private final PaymentAttemptRepository attemptRepository;

    @Transactional(readOnly = true)
    public PaymentCheckoutResponse resumePayment(UUID customerId, UUID paymentId) {
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        var payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        BookingPaymentSnapshot booking;
        try {
            booking = bookingFacade.getAccessibleBooking(customerId, Set.of("ROLE_CUSTOMER"), payment.getBookingId());
        } catch (ApiException exception) {
            if (exception.getErrorCode().status() == HttpStatus.NOT_FOUND) {
                throw new ApiException(PaymentErrorCode.PAYMENT_NOT_FOUND);
            }
            throw exception;
        }
        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            throw new ApiException(PaymentErrorCode.PAYMENT_ALREADY_SUCCEEDED);
        }
        Instant now = Instant.now();
        requirePayableBooking(booking, now);
        requirePayablePayment(payment, booking, now);
        var attempt = attemptRepository.findByPaymentIdAndStatusIn(paymentId, ACTIVE)
                .orElseThrow(() -> new ApiException(PaymentErrorCode.PAYMENT_CHECKOUT_NOT_ALLOWED));
        now = Instant.now();
        requirePayableBooking(booking, now);
        requirePayablePayment(payment, booking, now);
        requireResumable(attempt, now);
        return toResponse(payment, attempt);
    }
}
