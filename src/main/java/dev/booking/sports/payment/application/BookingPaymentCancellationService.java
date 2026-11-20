package dev.booking.sports.payment.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.booking.sports.booking.domain.event.BookingCancelledEvent;
import dev.booking.sports.booking.publicapi.BookingPaymentFacade;
import dev.booking.sports.payment.domain.enums.PaymentStatus;
import dev.booking.sports.payment.domain.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingPaymentCancellationService {

    private final BookingPaymentFacade bookings;
    private final PaymentRepository payments;

    @Transactional
    public void cancelPendingPayment(BookingCancelledEvent event) {
        if (!bookings.lockCancelledBooking(event.bookingId())) {
            throw new IllegalArgumentException("Expected a cancelled booking");
        }
        payments.findByBookingIdForUpdate(event.bookingId()).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.cancel(event.cancelledBy(), event.cancellationReason());
                payments.flush();
            }
        });
    }
}
