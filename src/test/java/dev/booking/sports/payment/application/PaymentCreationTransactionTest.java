package dev.booking.sports.payment.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import dev.booking.sports.booking.publicapi.BookingPaymentFacade;
import dev.booking.sports.booking.publicapi.BookingPaymentSnapshot;
import dev.booking.sports.payment.api.error.PaymentErrorCode;
import dev.booking.sports.payment.api.dto.response.PaymentCheckoutResponse;
import dev.booking.sports.payment.domain.enums.*;
import dev.booking.sports.payment.domain.model.*;
import dev.booking.sports.payment.domain.repository.*;
import dev.booking.sports.payment.infrastructure.sepay.SePayCheckoutFactory;
import dev.booking.sports.shared.exception.ApiException;

@ExtendWith(MockitoExtension.class)
class PaymentCreationTransactionTest {
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final UUID BOOKING = UUID.randomUUID();
    private static final String KEY = "checkout-1";
    private static final String HASH = "a".repeat(64);

    @Mock private BookingPaymentFacade bookingFacade;
    @Mock private PaymentRepository payments;
    @Mock private PaymentAttemptRepository attempts;
    @Mock private SePayCheckoutFactory checkoutFactory;

    private BookingPaymentSnapshot booking;
    private PaymentCreationTransaction transaction;
    private MockedStatic<Instant> time;

    @BeforeEach
    void setUp() {
        time = mockStatic(Instant.class, CALLS_REAL_METHODS);
        time.when(Instant::now).thenReturn(NOW);
        booking = new BookingPaymentSnapshot(BOOKING, "BK001", CUSTOMER, new BigDecimal("300000"),
                "VND", NOW.plusSeconds(600), true);
        transaction = new PaymentCreationTransaction(bookingFacade, payments, attempts, checkoutFactory);
        when(bookingFacade.lockOwnedBooking(CUSTOMER, BOOKING)).thenReturn(booking);
    }

    @AfterEach
    void tearDown() {
        time.close();
    }

    @Test
    void createsFromServerPriceAndPreservesOriginalDeadline() {
        stubNewPayment();
        stubNewAttempt();
        var response = create();
        assertThat(response.amount()).isEqualByComparingTo("300000");
        assertThat(response.expiresAt()).isEqualTo(booking.holdExpiresAt());
        assertThat(response.checkout().action()).isEqualTo("https://pay-sandbox.sepay.vn/v1/checkout/init");
        assertThat(response.checkout().method()).isEqualTo("POST");
        assertThat(response.checkout().fields()).extracting(field -> field.name())
                .containsExactly("order_amount", "signature");
        var captured = ArgumentCaptor.forClass(PaymentAttempt.class);
        verify(attempts).saveAndFlush(captured.capture());
        assertThat(captured.getValue().getAttemptNumber()).isEqualTo(1);
        assertThat(captured.getValue().getIdempotencyRequestHash()).isEqualTo(HASH);
        var order = inOrder(bookingFacade, payments);
        order.verify(bookingFacade).lockOwnedBooking(CUSTOMER, BOOKING);
        order.verify(payments).findByBookingIdForUpdate(BOOKING);
        verify(bookingFacade).startPayment(CUSTOMER, BOOKING, NOW);
        verify(payments).flush();
    }

    @Test
    void sameKeyReturnsExactPersistedFieldsWithoutSigningOrCreatingAgain() {
        Payment payment = existingPayment();
        PaymentAttempt attempt = attempt(payment, KEY, HASH);
        when(attempts.findByPaymentIdAndIdempotencyKey(payment.getId(), KEY)).thenReturn(Optional.of(attempt));
        var response = create();
        assertThat(response.attemptId()).isEqualTo(attempt.getId());
        assertThat(response.checkout().fields().getLast().value()).isEqualTo("persisted-signature");
        verifyNoInteractions(checkoutFactory);
        verify(attempts, never()).saveAndFlush(any());
        verify(payments, never()).saveAndFlush(any());
    }

    @Test
    void sameKeyWithChangedMethodIsRejectedBeforeReturningOldCheckout() {
        Payment payment = existingPayment();
        when(attempts.findByPaymentIdAndIdempotencyKey(payment.getId(), KEY))
                .thenReturn(Optional.of(attempt(payment, KEY, "b".repeat(64))));
        assertError(PaymentErrorCode.PAYMENT_IDEMPOTENCY_KEY_REUSED);
        verifyNoInteractions(checkoutFactory);
    }

    @Test
    void differentKeyCannotCreateASecondActiveInvoice() {
        Payment payment = existingPayment();
        when(attempts.findByPaymentIdAndStatusIn(eq(payment.getId()), anyCollection()))
                .thenReturn(Optional.of(attempt(payment, "other-key", HASH)));
        assertError(PaymentErrorCode.PAYMENT_ATTEMPT_IN_PROGRESS);
        verifyNoInteractions(checkoutFactory);
    }

    @Test
    void unknownAttemptMustBeReconciledBeforeAnotherInvoice() {
        Payment payment = existingPayment();
        PaymentAttempt attempt = attempt(payment, "other-key", HASH);
        attempt.markUnknown("Timeout", NOW.plusSeconds(30));
        when(attempts.findByPaymentIdAndStatusIn(eq(payment.getId()), anyCollection())).thenReturn(Optional.of(attempt));
        assertError(PaymentErrorCode.PAYMENT_OUTCOME_UNKNOWN);
    }

    @Test
    void deadlineIsEnforcedEvenWhenBookingExpiryJobHasNotRun() {
        when(bookingFacade.lockOwnedBooking(CUSTOMER, BOOKING)).thenReturn(new BookingPaymentSnapshot(
                BOOKING, "BK001", CUSTOMER, booking.totalAmount(), "VND", NOW, true));
        assertError(PaymentErrorCode.PAYMENT_EXPIRED);
        verify(payments, never()).saveAndFlush(any());
        verifyNoInteractions(checkoutFactory);
    }

    @Test
    void unsupportedCurrencyAndFractionalDongFailBeforeWritingPayment() {
        when(bookingFacade.lockOwnedBooking(CUSTOMER, BOOKING)).thenReturn(new BookingPaymentSnapshot(
                BOOKING, "BK001", CUSTOMER, booking.totalAmount(), "USD", booking.holdExpiresAt(), true));
        assertError(PaymentErrorCode.PAYMENT_CURRENCY_NOT_SUPPORTED);
        when(bookingFacade.lockOwnedBooking(CUSTOMER, BOOKING)).thenReturn(new BookingPaymentSnapshot(
                BOOKING, "BK001", CUSTOMER, new BigDecimal("300000.01"), "VND", booking.holdExpiresAt(), true));
        assertError(PaymentErrorCode.PAYMENT_AMOUNT_INVALID);
        verify(payments, never()).saveAndFlush(any());
    }

    @Test
    void refusesCancelledBooking() {
        when(bookingFacade.lockOwnedBooking(CUSTOMER, BOOKING)).thenReturn(new BookingPaymentSnapshot(
                BOOKING, "BK001", CUSTOMER, booking.totalAmount(), "VND", booking.holdExpiresAt(), false));
        assertError(PaymentErrorCode.PAYMENT_BOOKING_NOT_PAYABLE);
    }

    @Test
    void refusesAlreadyPaidPayment() {
        Payment payment = existingPayment();
        ReflectionTestUtils.setField(payment, "status", PaymentStatus.SUCCEEDED);
        assertError(PaymentErrorCode.PAYMENT_ALREADY_SUCCEEDED);
        verifyNoInteractions(checkoutFactory);
    }

    @Test
    void rejectsChangedBookingPriceRatherThanReusingWrongAmount() {
        existingPayment();
        when(bookingFacade.lockOwnedBooking(CUSTOMER, BOOKING)).thenReturn(new BookingPaymentSnapshot(
                BOOKING, "BK001", CUSTOMER, new BigDecimal("350000"), "VND", booking.holdExpiresAt(), true));
        assertError(PaymentErrorCode.PAYMENT_BOOKING_CHANGED);
    }

    @Test
    void verifiedCancelledOrderCanHaveANewAttemptButOldKeyCannotBeRepurposed() {
        Payment payment = existingPayment();
        PaymentAttempt old = attempt(payment, "old-key", HASH);
        old.recordGatewayOutcome(PaymentAttemptStatus.CANCELLED, "old-order", "CANCELLED");
        when(attempts.findFirstByPaymentIdOrderByAttemptNumberDesc(payment.getId())).thenReturn(Optional.of(old));
        stubNewAttempt();
        create();
        var captured = ArgumentCaptor.forClass(PaymentAttempt.class);
        verify(attempts).saveAndFlush(captured.capture());
        assertThat(captured.getValue().getAttemptNumber()).isEqualTo(2);
        assertThat(captured.getValue().getInvoiceNumber()).isNotEqualTo(old.getInvoiceNumber());
    }

    @Test
    void replayOfCancelledAttemptDoesNotCreateAnotherInvoice() {
        Payment payment = existingPayment();
        PaymentAttempt old = attempt(payment, KEY, HASH);
        old.recordGatewayOutcome(PaymentAttemptStatus.CANCELLED, "old-order", "CANCELLED");
        when(attempts.findByPaymentIdAndIdempotencyKey(payment.getId(), KEY)).thenReturn(Optional.of(old));
        assertError(PaymentErrorCode.PAYMENT_CHECKOUT_NOT_ALLOWED);
        verifyNoInteractions(checkoutFactory);
    }

    @Test
    void refusesADeadlineThatPassesDuringCheckoutPreparation() {
        stubNewPayment();
        when(checkoutFactory.create(any(), eq(booking), eq(CUSTOMER), isNull(), eq(KEY), eq(HASH))).thenReturn(spec(KEY, HASH));
        time.when(Instant::now).thenReturn(NOW, booking.holdExpiresAt());
        assertError(PaymentErrorCode.PAYMENT_EXPIRED);
        verify(attempts, never()).saveAndFlush(any());
        verify(bookingFacade, never()).startPayment(any(), any(), any());
    }

    private Payment existingPayment() {
        Payment payment = Payment.pending(BOOKING, booking.totalAmount(), "VND", booking.holdExpiresAt(), NOW);
        payment.setId(UUID.randomUUID());
        when(payments.findByBookingIdForUpdate(BOOKING)).thenReturn(Optional.of(payment));
        return payment;
    }

    private void stubNewPayment() {
        when(payments.saveAndFlush(any())).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(UUID.randomUUID());
            return payment;
        });
    }

    private void stubNewAttempt() {
        when(checkoutFactory.create(any(), eq(booking), eq(CUSTOMER), isNull(), eq(KEY), eq(HASH))).thenReturn(spec(KEY, HASH));
        when(attempts.saveAndFlush(any())).thenAnswer(invocation -> {
            PaymentAttempt attempt = invocation.getArgument(0);
            attempt.setId(UUID.randomUUID());
            return attempt;
        });
    }

    private PaymentAttempt attempt(Payment payment, String key, String hash) {
        PaymentAttempt attempt = PaymentAttempt.prepare(payment, 1, new PaymentAttempt.CheckoutSpec(
                PaymentEnvironment.SANDBOX, "merchant", "OLD-INVOICE", null, spec(key, hash).fields(), key, hash), NOW);
        attempt.setId(UUID.randomUUID());
        return attempt;
    }

    private PaymentAttempt.CheckoutSpec spec(String key, String hash) {
        return new PaymentAttempt.CheckoutSpec(PaymentEnvironment.SANDBOX, "merchant", "NEW-INVOICE", null,
                List.of(new PaymentAttempt.CheckoutField("order_amount", "300000"),
                    new PaymentAttempt.CheckoutField("signature", "persisted-signature")), key, hash);
    }

    private PaymentCheckoutResponse create() {
        return transaction.create(CUSTOMER, BOOKING, KEY, HASH, null);
    }

    private void assertError(PaymentErrorCode code) {
        assertThatThrownBy(this::create).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
