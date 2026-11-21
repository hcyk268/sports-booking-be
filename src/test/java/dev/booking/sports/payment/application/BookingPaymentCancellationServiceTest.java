package dev.booking.sports.payment.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.booking.sports.booking.domain.event.BookingCancelledEvent;
import dev.booking.sports.booking.publicapi.BookingPaymentFacade;
import dev.booking.sports.payment.domain.enums.PaymentStatus;
import dev.booking.sports.payment.domain.model.Payment;
import dev.booking.sports.payment.domain.repository.PaymentRepository;

class BookingPaymentCancellationServiceTest {
    private final BookingPaymentFacade bookings = mock(BookingPaymentFacade.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final BookingPaymentCancellationService service = new BookingPaymentCancellationService(bookings, payments);
    private final BookingCancelledEvent event = new BookingCancelledEvent(UUID.randomUUID(), "BK-test",
            UUID.randomUUID(), new BigDecimal("50000"), "VND", UUID.randomUUID(), "Change of plans",
            Instant.now(), List.of());

    @Test
    void cancelsOnceAndLocksBookingBeforePayment() {
        var payment = Payment.pending(event.bookingId(), event.totalAmount(), "VND",
                Instant.now().plusSeconds(600), Instant.now());
        when(bookings.lockCancelledBooking(event.bookingId())).thenReturn(true);
        when(payments.findByBookingIdForUpdate(event.bookingId())).thenReturn(Optional.of(payment));

        service.cancelPendingPayment(event);
        service.cancelPendingPayment(event);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(payment.getStatusHistory()).hasSize(2);
        var history = payment.getStatusHistory().getLast();
        assertThat(history.getChangedBy()).isEqualTo(event.cancelledBy());
        assertThat(history.getReason()).isEqualTo(event.cancellationReason());
        var order = inOrder(bookings, payments);
        order.verify(bookings).lockCancelledBooking(event.bookingId());
        order.verify(payments).findByBookingIdForUpdate(event.bookingId());
        order.verify(payments).flush();
        verify(payments, times(1)).flush();
    }

    @Test
    void noPaymentIsSuccessfulNoOp() {
        when(bookings.lockCancelledBooking(event.bookingId())).thenReturn(true);
        when(payments.findByBookingIdForUpdate(event.bookingId())).thenReturn(Optional.empty());
        service.cancelPendingPayment(event);
        verify(payments, never()).flush();
    }

    @Test
    void leavesCollectedAndTerminalPaymentsUnchanged() {
        when(bookings.lockCancelledBooking(event.bookingId())).thenReturn(true);
        for (var status : List.of(PaymentStatus.SUCCEEDED, PaymentStatus.CANCELLED, PaymentStatus.EXPIRED)) {
            var payment = mock(Payment.class);
            when(payment.getStatus()).thenReturn(status);
            when(payments.findByBookingIdForUpdate(event.bookingId())).thenReturn(Optional.of(payment));
            service.cancelPendingPayment(event);
            verify(payment, never()).cancel(any(), any());
        }
        verify(payments, never()).flush();
    }

    @Test
    void rejectsEventWhenBookingIsMissingOrNotCancelled() {
        when(bookings.lockCancelledBooking(event.bookingId())).thenReturn(false);
        assertThatThrownBy(() -> service.cancelPendingPayment(event)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(payments);
    }
}
