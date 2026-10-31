package dev.booking.sports.payment.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import dev.booking.sports.payment.api.dto.request.CreatePaymentRequest;
import dev.booking.sports.payment.api.error.PaymentErrorCode;
import dev.booking.sports.payment.infrastructure.redis.PaymentLockManager;
import dev.booking.sports.shared.exception.ApiException;

class BookingPaymentServiceTest {
    @Test
    void hashesRedisKeyButPersistsRawKeyAndKeepsLockUntilTransactionalBeanReturns() {
        PaymentLockManager locks = mock(PaymentLockManager.class);
        PaymentLockManager.LockHandle handle = mock(PaymentLockManager.LockHandle.class);
        PaymentCreationTransaction transaction = mock(PaymentCreationTransaction.class);
        BookingPaymentService service = new BookingPaymentService(locks, transaction);
        UUID customer = UUID.randomUUID();
        UUID booking = UUID.randomUUID();
        when(locks.acquireIdempotency(eq(customer), anyString())).thenReturn(handle);
        when(transaction.create(eq(customer), eq(booking), eq("raw-key"), anyString(), eq("BANK_TRANSFER")))
                .thenAnswer(invocation -> {
                    verify(handle, never()).close();
                    throw new IllegalStateException("Transaction rolled back");
                });
        assertThatThrownBy(() -> service.createPayment(customer, booking, "raw-key", new CreatePaymentRequest("BANK_TRANSFER")))
                .isInstanceOf(IllegalStateException.class);
        var order = inOrder(locks, transaction, handle);
        order.verify(locks).acquireIdempotency(eq(customer), argThat(key -> key.matches("[0-9a-f]{64}")));
        order.verify(transaction).create(eq(customer), eq(booking), eq("raw-key"), anyString(), eq("BANK_TRANSFER"));
        order.verify(handle).close();
    }

    @Test
    void hashIncludesBookingAndSelectedMethod() {
        UUID booking = UUID.randomUUID();
        String hash = BookingPaymentService.requestHash(booking, null);
        assertThat(hash).matches("[0-9a-f]{64}")
                .isEqualTo(BookingPaymentService.requestHash(booking, null))
                .isNotEqualTo(BookingPaymentService.requestHash(booking, "BANK_TRANSFER"))
                .isNotEqualTo(BookingPaymentService.requestHash(UUID.randomUUID(), null));
    }

    @Test
    void invalidMethodDoesNotAcquireLockOrCallTransaction() {
        PaymentLockManager locks = mock(PaymentLockManager.class);
        PaymentCreationTransaction transaction = mock(PaymentCreationTransaction.class);
        BookingPaymentService service = new BookingPaymentService(locks, transaction);
        assertThatThrownBy(() -> service.createPayment(UUID.randomUUID(), UUID.randomUUID(), "key",
                new CreatePaymentRequest("INVALID")))
                .isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.getErrorCode())
                    .isEqualTo(PaymentErrorCode.PAYMENT_METHOD_NOT_SUPPORTED));
        verifyNoInteractions(locks, transaction);
    }
}
