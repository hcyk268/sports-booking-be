package dev.booking.sports.payment.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import dev.booking.sports.booking.api.error.BookingErrorCode;
import dev.booking.sports.booking.publicapi.BookingPaymentFacade;
import dev.booking.sports.booking.publicapi.BookingPaymentSnapshot;
import dev.booking.sports.payment.api.error.PaymentErrorCode;
import dev.booking.sports.payment.domain.enums.*;
import dev.booking.sports.payment.domain.model.Payment;
import dev.booking.sports.payment.domain.model.PaymentAttempt;
import dev.booking.sports.payment.domain.repository.PaymentAttemptRepository;
import dev.booking.sports.payment.domain.repository.PaymentRepository;
import dev.booking.sports.shared.exception.ApiException;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final UUID BOOKING = UUID.randomUUID();
    @Mock private PaymentRepository payments;
    @Mock private BookingPaymentFacade bookings;
    @Mock private PaymentAttemptRepository attempts;
    private PaymentService service;
    private Payment payment;
    private PaymentAttempt attempt;
    private BookingPaymentSnapshot booking;
    private MockedStatic<Instant> time;

    @BeforeEach
    void setUp() {
        time = mockStatic(Instant.class, CALLS_REAL_METHODS);
        time.when(Instant::now).thenReturn(NOW);
        service = new PaymentService(payments, bookings, attempts);
        payment = Payment.pending(BOOKING, new BigDecimal("300000"), "VND", NOW.plusSeconds(600), NOW);
        payment.setId(UUID.randomUUID());
        booking = snapshot(payment.getAmount(), payment.getExpiresAt(), true);
        attempt = PaymentAttempt.prepare(payment, 1, new PaymentAttempt.CheckoutSpec(
                PaymentEnvironment.PRODUCTION, "merchant", "original-invoice", "BANK_TRANSFER",
                List.of(new PaymentAttempt.CheckoutField("order_amount", "300000"),
                        new PaymentAttempt.CheckoutField("order_invoice_number", "original-invoice"),
                        new PaymentAttempt.CheckoutField("signature", "original-signature")),
                "original-key", "a".repeat(64)), NOW);
        attempt.setId(UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        time.close();
    }

    @ParameterizedTest
    @EnumSource(value = PaymentAttemptStatus.class, names = {"PREPARED", "PENDING"})
    void reusesExactPersistedCheckoutWithoutWritesOrAdditionalQueries(PaymentAttemptStatus status) {
        ReflectionTestUtils.setField(attempt, "status", status);
        accessiblePayment();
        activeAttempt();

        var result = service.resumePayment(CUSTOMER, payment.getId());

        assertThat(result.paymentId()).isEqualTo(payment.getId());
        assertThat(result.attemptId()).isEqualTo(attempt.getId());
        assertThat(result.expiresAt()).isEqualTo(payment.getExpiresAt());
        assertThat(result.checkout().action()).isEqualTo("https://pay.sepay.vn/v1/checkout/init");
        assertThat(result.checkout().method()).isEqualTo("POST");
        assertThat(result.checkout().fields()).extracting(field -> field.name())
                .containsExactly("order_amount", "order_invoice_number", "signature");
        assertThat(result.checkout().fields()).extracting(field -> field.value())
                .containsExactly("300000", "original-invoice", "original-signature");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(attempt.getStatus()).isEqualTo(status);
        assertThat(attempt.getAttemptNumber()).isEqualTo(1);
        verify(payments).findById(payment.getId());
        verify(bookings).getAccessibleBooking(CUSTOMER, Set.of("ROLE_CUSTOMER"), BOOKING);
        verify(attempts).findByPaymentIdAndStatusIn(payment.getId(), PaymentCheckoutSupport.ACTIVE);
        verifyNoMoreInteractions(payments, bookings, attempts);
    }

    @Test
    void missingPaymentIsNotFoundBeforeLookingUpBookingOrAttempt() {
        assertError(PaymentErrorCode.PAYMENT_NOT_FOUND);
        verifyNoInteractions(bookings, attempts);
    }

    @Test
    void otherCustomersPaymentIsHiddenBeforeReadingSignedPayload() {
        when(payments.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(bookings.getAccessibleBooking(CUSTOMER, Set.of("ROLE_CUSTOMER"), BOOKING))
                .thenThrow(new ApiException(BookingErrorCode.BOOKING_NOT_FOUND));
        assertError(PaymentErrorCode.PAYMENT_NOT_FOUND);
        verifyNoInteractions(attempts);
    }

    @Test
    void paidPaymentIsRejectedEvenWhenBookingIsNoLongerAwaitingPayment() {
        booking = snapshot(payment.getAmount(), payment.getExpiresAt(), false);
        ReflectionTestUtils.setField(payment, "status", PaymentStatus.SUCCEEDED);
        accessiblePayment();
        assertError(PaymentErrorCode.PAYMENT_ALREADY_SUCCEEDED);
        verifyNoInteractions(attempts);
    }

    @Test
    void cancelledOrConfirmedBookingCannotResumeAPendingPayment() {
        booking = snapshot(payment.getAmount(), payment.getExpiresAt(), false);
        accessiblePayment();
        assertError(PaymentErrorCode.PAYMENT_BOOKING_NOT_PAYABLE);
        verifyNoInteractions(attempts);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"CANCELLED", "EXPIRED"})
    void terminalPaymentIsRejected(PaymentStatus status) {
        ReflectionTestUtils.setField(payment, "status", status);
        accessiblePayment();
        assertError(status == PaymentStatus.EXPIRED ? PaymentErrorCode.PAYMENT_EXPIRED
                : PaymentErrorCode.PAYMENT_CHECKOUT_NOT_ALLOWED);
        verifyNoInteractions(attempts);
    }

    @Test
    void exactHoldDeadlineIsExpiredEvenIfExpiryJobHasNotRun() {
        accessiblePayment();
        time.when(Instant::now).thenReturn(payment.getExpiresAt());
        assertError(PaymentErrorCode.PAYMENT_EXPIRED);
        verifyNoInteractions(attempts);
    }

    @Test
    void independentlyExpiredPaymentCannotResume() {
        ReflectionTestUtils.setField(payment, "expiresAt", NOW);
        accessiblePayment();
        assertError(PaymentErrorCode.PAYMENT_EXPIRED);
        verifyNoInteractions(attempts);
    }

    @Test
    void changedBookingPriceCannotReuseTheOldForm() {
        booking = snapshot(new BigDecimal("350000"), payment.getExpiresAt(), true);
        accessiblePayment();
        assertError(PaymentErrorCode.PAYMENT_BOOKING_CHANGED);
        verifyNoInteractions(attempts);
    }

    @Test
    void changedBookingDeadlineCannotReuseTheOldForm() {
        booking = snapshot(payment.getAmount(), payment.getExpiresAt().plusSeconds(60), true);
        accessiblePayment();
        assertError(PaymentErrorCode.PAYMENT_BOOKING_CHANGED);
        verifyNoInteractions(attempts);
    }

    @Test
    void unknownAttemptRequiresReconciliation() {
        attempt.markUnknown("Uncertain gateway outcome", NOW.plusSeconds(30));
        accessiblePayment();
        activeAttempt();
        assertError(PaymentErrorCode.PAYMENT_OUTCOME_UNKNOWN);
    }

    @Test
    void noActiveAttemptDoesNotCreateOrResumeATerminalAttempt() {
        accessiblePayment();
        assertError(PaymentErrorCode.PAYMENT_CHECKOUT_NOT_ALLOWED);
        verify(attempts).findByPaymentIdAndStatusIn(payment.getId(), PaymentCheckoutSupport.ACTIVE);
        verifyNoMoreInteractions(attempts);
    }

    @Test
    void expiredAttemptCannotResumeEvenWhilePaymentRemainsPending() {
        ReflectionTestUtils.setField(attempt, "expiresAt", NOW);
        accessiblePayment();
        activeAttempt();
        assertError(PaymentErrorCode.PAYMENT_CHECKOUT_NOT_ALLOWED);
    }

    @Test
    void deadlinePassingDuringAttemptQueryIsRechecked() {
        accessiblePayment();
        activeAttempt();
        time.when(Instant::now).thenReturn(NOW, payment.getExpiresAt());
        assertError(PaymentErrorCode.PAYMENT_EXPIRED);
    }

    private BookingPaymentSnapshot snapshot(BigDecimal amount, Instant deadline, boolean awaiting) {
        return new BookingPaymentSnapshot(BOOKING, "BK001", CUSTOMER, amount, "VND", deadline, awaiting);
    }

    private void accessiblePayment() {
        when(payments.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(bookings.getAccessibleBooking(CUSTOMER, Set.of("ROLE_CUSTOMER"), BOOKING)).thenReturn(booking);
    }

    private void activeAttempt() {
        when(attempts.findByPaymentIdAndStatusIn(payment.getId(), PaymentCheckoutSupport.ACTIVE))
                .thenReturn(Optional.of(attempt));
    }

    private void assertError(PaymentErrorCode code) {
        assertThatThrownBy(() -> service.resumePayment(CUSTOMER, payment.getId()))
                .isInstanceOfSatisfying(ApiException.class,
                    exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
