package dev.booking.sports.payment.application;

import static dev.booking.sports.payment.application.PaymentCheckoutSupport.*;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import dev.booking.sports.booking.publicapi.BookingPaymentFacade;
import dev.booking.sports.booking.publicapi.BookingPaymentSnapshot;
import dev.booking.sports.payment.api.dto.response.PaymentCheckoutResponse;
import dev.booking.sports.payment.api.error.PaymentErrorCode;
import dev.booking.sports.payment.domain.enums.PaymentAttemptStatus;
import dev.booking.sports.payment.domain.enums.PaymentStatus;
import dev.booking.sports.payment.domain.model.Payment;
import dev.booking.sports.payment.domain.model.PaymentAttempt;
import dev.booking.sports.payment.domain.repository.PaymentAttemptRepository;
import dev.booking.sports.payment.domain.repository.PaymentRepository;
import dev.booking.sports.payment.infrastructure.sepay.SePayCheckoutFactory;
import dev.booking.sports.shared.exception.ApiException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentCreationTransaction {

    private final BookingPaymentFacade bookingFacade;
    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final SePayCheckoutFactory checkoutFactory;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentCheckoutResponse create(UUID customerId, UUID bookingId, String idempotencyKey,
            String requestHash, String paymentMethod) {
        BookingPaymentSnapshot booking = bookingFacade.lockOwnedBooking(customerId, bookingId);
        Instant now = Instant.now();
        Payment payment = paymentRepository.findByBookingIdForUpdate(bookingId).orElse(null);
        PaymentAttempt replay = payment == null ? null : attemptRepository
                .findByPaymentIdAndIdempotencyKey(payment.getId(), idempotencyKey).orElse(null);
        if (replay != null && !replay.hasIdempotencyRequestHash(requestHash)) {
            throw new ApiException(PaymentErrorCode.PAYMENT_IDEMPOTENCY_KEY_REUSED);
        }
        if (payment != null && payment.getStatus() == PaymentStatus.SUCCEEDED) {
            throw new ApiException(PaymentErrorCode.PAYMENT_ALREADY_SUCCEEDED);
        }
        requirePayableBooking(booking, now);
        if (payment == null) {
            payment = paymentRepository.saveAndFlush(Payment.pending(
                    bookingId, booking.totalAmount(), booking.currency(), booking.holdExpiresAt(), now));
        }
        requirePayablePayment(payment, booking, now);

        if (replay != null) {
            requireResumable(replay, now);
            return finish(customerId, bookingId, payment, replay, booking);
        }

        var active = attemptRepository.findByPaymentIdAndStatusIn(payment.getId(), ACTIVE);
        if (active.isPresent()) {
            if (active.get().getStatus() == PaymentAttemptStatus.UNKNOWN) {
                throw new ApiException(PaymentErrorCode.PAYMENT_OUTCOME_UNKNOWN);
            }
            throw new ApiException(PaymentErrorCode.PAYMENT_ATTEMPT_IN_PROGRESS);
        }
        var previous = attemptRepository.findFirstByPaymentIdOrderByAttemptNumberDesc(payment.getId());
        int attemptNumber = 1;
        if (previous.isPresent()) {
            PaymentAttempt old = previous.get();
            if ((old.getStatus() != PaymentAttemptStatus.FAILED && old.getStatus() != PaymentAttemptStatus.CANCELLED)
                    || old.getProviderOrderId() == null || old.getProviderOrderStatus() == null) {
                throw new ApiException(PaymentErrorCode.PAYMENT_OUTCOME_UNKNOWN);
            }
            attemptNumber = Math.addExact(old.getAttemptNumber(), 1);
        }
        var spec = checkoutFactory.create(payment, booking, customerId, paymentMethod, idempotencyKey, requestHash);
        Instant preparedAt = Instant.now();
        requirePayablePayment(payment, booking, preparedAt);
        PaymentAttempt attempt = attemptRepository.saveAndFlush(
                PaymentAttempt.prepare(payment, attemptNumber, spec, preparedAt));
        return finish(customerId, bookingId, payment, attempt, booking);
    }

    private PaymentCheckoutResponse finish(UUID customerId, UUID bookingId, Payment payment,
            PaymentAttempt attempt, BookingPaymentSnapshot booking) {
        Instant now = Instant.now();
        requirePayableBooking(booking, now);
        requirePayablePayment(payment, booking, now);
        requireResumable(attempt, now);
        bookingFacade.startPayment(customerId, bookingId, now);
        // Flush also validates the booking/slot/history changes in this transaction.
        paymentRepository.flush();
        return toResponse(payment, attempt);
    }
}
