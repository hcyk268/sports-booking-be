package dev.booking.sports.payment.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import dev.booking.sports.booking.publicapi.*;
import dev.booking.sports.payment.domain.enums.*;
import dev.booking.sports.payment.domain.model.*;
import dev.booking.sports.payment.domain.repository.*;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import dev.booking.sports.payment.api.error.PaymentErrorCode;
import dev.booking.sports.booking.api.error.BookingErrorCode;
import dev.booking.sports.shared.exception.ApiException;

@ExtendWith(MockitoExtension.class)
class PaymentQueryServiceTest {
    @Mock private BookingPaymentFacade facade;
    @Mock private PaymentRepository payments;
    @Mock private PaymentAttemptRepository attempts;
    @Mock private PaymentTransactionRepository transactions;
    @Mock private PaymentRefundRepository refunds;
    private final UUID actor = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();
    private final Set<String> roles = Set.of("ROLE_CUSTOMER");
    private PaymentQueryService service;
    private Payment payment;
    private PaymentAttempt attempt;
    private Instant now;

    @BeforeEach
    void setUp() {
        service = new PaymentQueryService(facade, payments, attempts, transactions, refunds);
        now = Instant.now();
        payment = Payment.pending(bookingId, new BigDecimal("300000"), "VND", now.plusSeconds(600), now);
        payment.setId(UUID.randomUUID());
        attempt = PaymentAttempt.prepare(payment, 1, new PaymentAttempt.CheckoutSpec(
                PaymentEnvironment.SANDBOX, "merchant", "invoice", null,
                List.of(new PaymentAttempt.CheckoutField("signature", "private-signature")), "key", "a".repeat(64)), now);
        attempt.setId(UUID.randomUUID());
    }

    @Test
    void checksBookingAccessBeforeReturningAnEmptyList() {
        accessible(true, payment.getExpiresAt());
        assertThat(service.getBookingPayments(actor, roles, bookingId)).isEmpty();
        var order = inOrder(facade, payments);
        order.verify(facade).getAccessibleBooking(actor, roles, bookingId);
        order.verify(payments).findByBookingId(bookingId);
        verifyNoInteractions(attempts, transactions, refunds);
    }

    @Test
    void deniesAccessBeforeQueryingAnyPayment() {
        when(facade.getAccessibleBooking(actor, roles, bookingId)).thenThrow(new IllegalStateException("Not accessible"));
        assertThatThrownBy(() -> service.getBookingPayments(actor, roles, bookingId)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(payments, attempts, transactions, refunds);
    }

    @Test
    void returnsFullReadModelWithoutSignedFieldsOrAdminRefundNotes() {
        accessible(true, payment.getExpiresAt());
        existing();
        var receipt = PaymentTransaction.record(new PaymentTransaction.Receipt(PaymentEnvironment.SANDBOX,
                "merchant", "tx1", "reference", null, "invoice", "PAYMENT", "APPROVED", "BANK_TRANSFER",
                payment.getAmount(), "VND", now), now);
        receipt.setId(UUID.randomUUID());
        receipt.match(attempt);
        var refund = PaymentRefund.request(receipt, payment.getAmount(), "Cancel", "refund", "b".repeat(64), actor);
        refund.setId(UUID.randomUUID());
        ReflectionTestUtils.setField(refund, "adminNote", "private-admin-note");
        when(transactions.findByPaymentIdOrderByReceivedAtDescIdDesc(payment.getId())).thenReturn(List.of(receipt));
        when(refunds.findByPaymentIdOrderByCreatedAtDescIdDesc(payment.getId())).thenReturn(List.of(refund));
        var result = service.getBookingPayments(actor, roles, bookingId).getFirst();
        assertThat(result.canResumeCheckout()).isTrue();
        assertThat(result.attempts().getFirst().invoiceNumber()).isEqualTo("invoice");
        assertThat(result.transactions().getFirst().providerTransactionId()).isEqualTo("tx1");
        assertThat(result.refunds().getFirst().id()).isEqualTo(refund.getId());
        assertThat(new ObjectMapper().writeValueAsString(result)).doesNotContain("private-signature", "private-admin-note", "checkoutPayload");
        verify(payments, never()).findByBookingIdForUpdate(any());
        verify(facade, never()).startPayment(any(), any(), any());
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void expiredOrCancelledBookingCannotResumeEvenIfPaymentStillPending() {
        existing();
        accessible(true, now.minusSeconds(1));
        assertThat(service.getBookingPayments(actor, roles, bookingId).getFirst().canResumeCheckout()).isFalse();
        accessible(false, payment.getExpiresAt());
        assertThat(service.getBookingPayments(actor, roles, bookingId).getFirst().canResumeCheckout()).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void unknownAttemptOrCancelledPaymentCannotResume() {
        accessible(true, payment.getExpiresAt());
        existing();
        attempt.markUnknown("Waiting for gateway", now.plusSeconds(30));
        assertThat(service.getBookingPayments(actor, roles, bookingId).getFirst().canResumeCheckout()).isFalse();
        attempt.markPending("order", "AUTHENTICATION_NOT_NEEDED");
        payment.cancel(actor, "Cancel");
        assertThat(service.getBookingPayments(actor, roles, bookingId).getFirst().canResumeCheckout()).isFalse();
    }

    private void accessible(boolean awaitingPayment, Instant deadline) {
        when(facade.getAccessibleBooking(actor, roles, bookingId)).thenReturn(new BookingPaymentSnapshot(
                bookingId, "BK1", actor, payment.getAmount(), "VND", deadline, awaitingPayment));
    }

    @Test
    void getsPaymentByIdAndUsesTheSameReadModel() {
        accessible(true, payment.getExpiresAt());
        when(payments.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(attempts.findByPaymentIdOrderByAttemptNumberDesc(payment.getId())).thenReturn(List.of(attempt));
        var result = service.getPayment(actor, roles, payment.getId());
        assertThat(result.id()).isEqualTo(payment.getId());
        assertThat(result.canResumeCheckout()).isTrue();
        verify(payments, never()).findByBookingIdForUpdate(any());
    }

    @Test
    void missingAndInaccessiblePaymentHaveTheSameNotFoundError() {
        assertThatThrownBy(() -> service.getPayment(actor, roles, payment.getId()))
                .isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.getErrorCode())
                    .isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND));
        when(payments.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(facade.getAccessibleBooking(actor, roles, bookingId)).thenThrow(new ApiException(BookingErrorCode.BOOKING_NOT_FOUND));
        assertThatThrownBy(() -> service.getPayment(actor, roles, payment.getId()))
                .isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.getErrorCode())
                    .isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND));
        verifyNoInteractions(attempts, transactions, refunds);
    }

    @Test
    void passesFiltersAndAllApplicableRolesBeforePagination() {
        var authorities = Set.of("ROLE_CUSTOMER", "ROLE_PROVIDER");
        UUID customerFilter = UUID.randomUUID();
        var pageable = PageRequest.of(2, 10);
        Instant from = now.minusSeconds(60);
        Instant to = now.plusSeconds(60);
        when(payments.findAccessiblePayments(actor, false, true, true, bookingId, customerFilter,
                "PENDING", from, to, pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 15));
        var result = service.listPayments(actor, authorities, bookingId, customerFilter, PaymentStatus.PENDING, from, to, 2, 10);
        assertThat(result.items()).isEmpty();
        assertThat(result.pagination()).isEqualTo(dev.booking.sports.shared.api.Pagination.from(2, 10, 15));
        verifyNoInteractions(facade, attempts, transactions, refunds);
    }

    @Test
    void listingLoadsRelationsOnceForTheWholePageAndKeepsThemWithTheCorrectPayment() {
        var authorities = Set.of("ROLE_ADMIN");
        var pageable = PageRequest.of(0, 20);
        var secondPayment = Payment.pending(UUID.randomUUID(), payment.getAmount(), "VND", payment.getExpiresAt(), now);
        secondPayment.setId(UUID.randomUUID());
        var secondAttempt = PaymentAttempt.prepare(secondPayment, 1, new PaymentAttempt.CheckoutSpec(
                PaymentEnvironment.SANDBOX, "merchant", "invoice2", null,
                List.of(new PaymentAttempt.CheckoutField("signature", "private-signature2")), "key2", "b".repeat(64)), now);
        secondAttempt.setId(UUID.randomUUID());
        var receipt = PaymentTransaction.record(new PaymentTransaction.Receipt(PaymentEnvironment.SANDBOX,
                "merchant", "tx2", "reference", null, "invoice2", "PAYMENT", "APPROVED", "BANK_TRANSFER",
                secondPayment.getAmount(), "VND", now), now);
        receipt.setId(UUID.randomUUID());
        receipt.match(secondAttempt);
        var refund = PaymentRefund.request(receipt, secondPayment.getAmount(), "Cancel", "refund", "c".repeat(64), actor);
        refund.setId(UUID.randomUUID());
        var paymentIds = List.of(payment.getId(), secondPayment.getId());
        var bookingIds = List.of(bookingId, secondPayment.getBookingId());
        when(payments.findAccessiblePayments(actor, true, false, false, null, null, null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(payment, secondPayment), pageable, 21));
        when(facade.getAccessibleBookings(actor, authorities, bookingIds)).thenReturn(Map.of(
                bookingId, new BookingPaymentSnapshot(bookingId, "BK1", actor, payment.getAmount(), "VND", payment.getExpiresAt(), true),
                secondPayment.getBookingId(), new BookingPaymentSnapshot(secondPayment.getBookingId(), "BK2", actor,
                        secondPayment.getAmount(), "VND", secondPayment.getExpiresAt(), false)));
        when(attempts.findByPaymentIdInOrderByAttemptNumberDesc(paymentIds)).thenReturn(List.of(secondAttempt, attempt));
        when(transactions.findByPaymentIdInOrderByReceivedAtDescIdDesc(paymentIds)).thenReturn(List.of(receipt));
        when(refunds.findByPaymentIdInOrderByCreatedAtDescIdDesc(paymentIds)).thenReturn(List.of(refund));
        var result = service.listPayments(actor, authorities, null, null, null, null, null, 0, 20);
        assertThat(result.items()).extracting(item -> item.id()).containsExactly(payment.getId(), secondPayment.getId());
        assertThat(result.items().getFirst()).satisfies(item -> {
            assertThat(item.attempts()).extracting(value -> value.id()).containsExactly(attempt.getId());
            assertThat(item.transactions()).isEmpty();
            assertThat(item.refunds()).isEmpty();
            assertThat(item.canResumeCheckout()).isTrue();
        });
        assertThat(result.items().getLast()).satisfies(item -> {
            assertThat(item.attempts()).extracting(value -> value.id()).containsExactly(secondAttempt.getId());
            assertThat(item.transactions()).extracting(value -> value.id()).containsExactly(receipt.getId());
            assertThat(item.refunds()).extracting(value -> value.id()).containsExactly(refund.getId());
            assertThat(item.canResumeCheckout()).isFalse();
        });
        assertThat(result.pagination()).isEqualTo(dev.booking.sports.shared.api.Pagination.from(0, 20, 21));
        verify(payments).findAccessiblePayments(actor, true, false, false, null, null, null, null, null, pageable);
        verify(facade).getAccessibleBookings(actor, authorities, bookingIds);
        verify(attempts).findByPaymentIdInOrderByAttemptNumberDesc(paymentIds);
        verify(transactions).findByPaymentIdInOrderByReceivedAtDescIdDesc(paymentIds);
        verify(refunds).findByPaymentIdInOrderByCreatedAtDescIdDesc(paymentIds);
        verifyNoMoreInteractions(payments, facade, attempts, transactions, refunds);
    }

    @Test
    void invalidPaginationDateRangeAndMissingRolesDoNotQueryDatabase() {
        assertThatThrownBy(() -> service.listPayments(actor, roles, null, null, null, null, null, -1, 20))
                .isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_PAGINATION));
        assertThatThrownBy(() -> service.listPayments(actor, roles, null, null, null, null, null, 0, 101))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.listPayments(actor, roles, null, null, null, now, now.minusSeconds(1), 0, 20))
                .isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.getErrorCode())
                    .isEqualTo(PaymentErrorCode.INVALID_PAYMENT_DATE_RANGE));
        assertThatThrownBy(() -> service.listPayments(actor, Set.of(), null, null, null, null, null, 0, 20))
                .isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.getErrorCode())
                    .isEqualTo(PaymentErrorCode.PAYMENT_ACCESS_DENIED));
        verifyNoInteractions(payments, facade, attempts, transactions, refunds);
    }

    private void existing() {
        when(payments.findByBookingId(bookingId)).thenReturn(Optional.of(payment));
        when(attempts.findByPaymentIdOrderByAttemptNumberDesc(payment.getId())).thenReturn(List.of(attempt));
    }
}
