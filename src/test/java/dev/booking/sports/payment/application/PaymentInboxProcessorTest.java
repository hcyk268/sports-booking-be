package dev.booking.sports.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import dev.booking.sports.booking.publicapi.BookingPaymentFacade;
import dev.booking.sports.booking.publicapi.BookingPaymentSnapshot;
import dev.booking.sports.payment.config.PaymentInboxProperties;
import dev.booking.sports.payment.domain.enums.PaymentAttemptStatus;
import dev.booking.sports.payment.domain.enums.PaymentEnvironment;
import dev.booking.sports.payment.domain.enums.PaymentInboxStatus;
import dev.booking.sports.payment.domain.enums.PaymentResolutionStatus;
import dev.booking.sports.payment.domain.enums.PaymentStatus;
import dev.booking.sports.payment.domain.event.PaymentOutboxMessageTypes;
import dev.booking.sports.payment.domain.model.Payment;
import dev.booking.sports.payment.domain.model.PaymentAttempt;
import dev.booking.sports.payment.domain.model.PaymentAttempt.CheckoutField;
import dev.booking.sports.payment.domain.model.PaymentAttempt.CheckoutSpec;
import dev.booking.sports.payment.domain.model.PaymentTransaction;
import dev.booking.sports.payment.domain.model.PaymentWebhookInbox;
import dev.booking.sports.payment.domain.repository.PaymentAttemptRepository;
import dev.booking.sports.payment.domain.repository.PaymentInboxRepository;
import dev.booking.sports.payment.domain.repository.PaymentInboxRepository.Claim;
import dev.booking.sports.payment.domain.repository.PaymentRepository;
import dev.booking.sports.payment.domain.repository.PaymentTransactionRepository;
import dev.booking.sports.shared.config.PaymentSettlementProperties;
import dev.booking.sports.shared.outbox.OutboxService;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import tools.jackson.databind.ObjectMapper;

class PaymentInboxProcessorTest {

    private final PaymentInboxRepository inboxes = mock(PaymentInboxRepository.class);
    private final PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentTransactionRepository transactions = mock(PaymentTransactionRepository.class);
    private final BookingPaymentFacade bookings = mock(BookingPaymentFacade.class);
    private final OutboxService outbox = mock(OutboxService.class);

    private ValidatorFactory validators;
    private PaymentInboxProcessor processor;
    private Payment payment;
    private PaymentAttempt attempt;
    private PaymentWebhookInbox inbox;
    private Claim claim;
    private PaymentTransaction savedReceipt;
    private UUID bookingId;
    private UUID customerId;
    private Instant createdAt;

    @BeforeEach
    void setUp() {
        createdAt = Instant.now();
        bookingId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        payment = Payment.pending(bookingId, new BigDecimal("50000"), "VND",
                createdAt.plusSeconds(600), createdAt);
        payment.setId(UUID.randomUUID());
        attempt = PaymentAttempt.prepare(payment, 1,
                new CheckoutSpec(PaymentEnvironment.SANDBOX, "merchant", "INV001", "BANK_TRANSFER",
                        List.of(new CheckoutField("signature", "signed")), "key", "a".repeat(64)), createdAt);
        attempt.setId(UUID.randomUUID());

        validators = Validation.buildDefaultValidatorFactory();
        processor = new PaymentInboxProcessor(inboxes, attempts, payments, transactions, bookings, outbox,
                new ObjectMapper(), validators.getValidator(), new PaymentInboxProperties(25, Duration.ofMinutes(5),
                        Duration.ofSeconds(15), 8), new PaymentSettlementProperties(Duration.ofMinutes(2)));
        createInbox(payload("ORDER_PAID", "CAPTURED", "APPROVED", "50000", "VND"), "ORDER_PAID",
                createdAt.plusSeconds(30));

        when(attempts.findPaymentIdByInvoice(PaymentEnvironment.SANDBOX, "merchant", "INV001"))
                .thenReturn(Optional.of(payment.getId()));
        when(payments.findBookingIdById(payment.getId())).thenReturn(Optional.of(bookingId));
        when(bookings.lockForPayment(bookingId)).thenReturn(Optional.of(snapshot(true, payment.getExpiresAt())));
        when(payments.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(attempts.findInvoiceForUpdate(PaymentEnvironment.SANDBOX, "merchant", "INV001"))
                .thenReturn(Optional.of(attempt));
        when(bookings.confirmPayment(eq(bookingId), any(Instant.class))).thenReturn(true);
        when(transactions.findByProviderAndEnvironmentAndMerchantIdAndProviderTransactionId(
                any(), any(), anyString(), anyString())).thenReturn(Optional.empty());
        when(transactions.saveAndFlush(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            savedReceipt = invocation.getArgument(0);
            savedReceipt.setId(UUID.randomUUID());
            return savedReceipt;
        });
    }

    @AfterEach
    void closeValidator() {
        validators.close();
    }

    @Test
    void settlesAndPublishesExactlyOnceThenTreatsDuplicateAsProcessed() {
        processor.process(claim);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.SUCCEEDED);
        assertThat(savedReceipt.getResolutionStatus()).isEqualTo(PaymentResolutionStatus.APPLIED);
        assertThat(inbox.getStatus()).isEqualTo(PaymentInboxStatus.PROCESSED);
        verify(transactions).flush();
        verify(outbox).enqueue(eq("PAYMENT"), eq(payment.getId()),
                eq(PaymentOutboxMessageTypes.SUCCEEDED), eq(PaymentOutboxMessageTypes.VERSION), any());

        PaymentWebhookInbox duplicate = newInbox(
                payload("ORDER_PAID", "CAPTURED", "APPROVED", "50000", "VND"), "ORDER_PAID",
                createdAt.plusSeconds(31));
        Claim duplicateClaim = claim(duplicate);
        when(inboxes.findForUpdate(duplicate.getId())).thenReturn(Optional.of(duplicate));
        when(transactions.findByProviderAndEnvironmentAndMerchantIdAndProviderTransactionId(
                any(), any(), anyString(), anyString())).thenReturn(Optional.of(savedReceipt));
        clearInvocations(bookings, outbox, transactions);

        processor.process(duplicateClaim);

        assertThat(duplicate.getStatus()).isEqualTo(PaymentInboxStatus.PROCESSED);
        verifyNoInteractions(bookings, outbox);
        verify(transactions, never()).saveAndFlush(any(PaymentTransaction.class));
    }

    @Test
    void financialMismatchIsRetainedForReviewAndPausesCheckout() {
        createInbox(payload("ORDER_PAID", "CAPTURED", "APPROVED", "60000", "VND"), "ORDER_PAID",
                createdAt.plusSeconds(30));

        processor.process(claim);

        assertThat(inbox.getStatus()).isEqualTo(PaymentInboxStatus.REVIEW_REQUIRED);
        assertThat(inbox.getLastError()).isEqualTo("AMOUNT_MISMATCH");
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.UNKNOWN);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(savedReceipt.getResolutionStatus()).isEqualTo(PaymentResolutionStatus.REVIEW_REQUIRED);
        verify(outbox).enqueue(eq("PAYMENT_INBOX"), eq(inbox.getId()),
                eq(PaymentOutboxMessageTypes.REVIEW_REQUIRED), eq(PaymentOutboxMessageTypes.VERSION), any());
    }

    @Test
    void voidAlwaysRequiresReconciliationAndDoesNotSettlePayment() {
        createInbox(payload("TRANSACTION_VOID", "VOIDED", "VOIDED", "50000", "VND"),
                "TRANSACTION_VOID", createdAt.plusSeconds(30));

        processor.process(claim);

        assertThat(inbox.getLastError()).isEqualTo("VOID_REQUIRES_RECONCILIATION");
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.UNKNOWN);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(savedReceipt.getResolutionStatus()).isEqualTo(PaymentResolutionStatus.REVIEW_REQUIRED);
        verify(bookings, never()).confirmPayment(any(), any());
    }

    @Test
    void onTimeGatewayPaymentCanSettleDuringGrace() {
        Instant deadline = createdAt.minusSeconds(10);
        ReflectionTestUtils.setField(payment, "expiresAt", deadline);
        ReflectionTestUtils.setField(attempt, "expiresAt", deadline);
        createInbox(payload("ORDER_PAID", "CAPTURED", "APPROVED", "50000", "VND"), "ORDER_PAID",
                deadline.minusSeconds(10));
        ReflectionTestUtils.setField(inbox, "receivedAt", deadline.plusSeconds(10));
        when(bookings.lockForPayment(bookingId)).thenReturn(Optional.of(snapshot(true, deadline)));

        processor.process(claim);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getSettledAt()).isEqualTo(deadline.minusSeconds(10));
        verify(bookings).confirmPayment(bookingId, deadline.minusSeconds(10));
    }

    @Test
    void providerPaymentAfterDeadlineRequiresReviewEvenInsideGrace() {
        Instant deadline = payment.getExpiresAt();
        createInbox(payload("ORDER_PAID", "CAPTURED", "APPROVED", "50000", "VND"), "ORDER_PAID",
                deadline.plusSeconds(1));

        processor.process(claim);

        assertThat(inbox.getLastError()).isEqualTo("LATE_PAYMENT");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(bookings, never()).confirmPayment(any(), any());
    }

    @Test
    void unmatchedInvoiceKeepsReceiptForManualReview() {
        when(attempts.findPaymentIdByInvoice(PaymentEnvironment.SANDBOX, "merchant", "INV001"))
                .thenReturn(Optional.empty());

        processor.process(claim);

        assertThat(savedReceipt).isNotNull();
        assertThat(savedReceipt.getPaymentId()).isNull();
        assertThat(inbox.getLastError()).isEqualTo("UNMATCHED_TRANSACTION");
    }

    @Test
    void lostLeaseCannotChangeFinancialState() {
        Claim stale = new Claim(inbox.getId(), UUID.randomUUID());

        processor.process(stale);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(inbox.getStatus()).isEqualTo(PaymentInboxStatus.PROCESSING);
        verifyNoInteractions(attempts, payments, transactions, bookings, outbox);
    }

    private void createInbox(String payload, String type, Instant occurredAt) {
        inbox = newInbox(payload, type, occurredAt);
        claim = claim(inbox);
        when(inboxes.findForUpdate(inbox.getId())).thenReturn(Optional.of(inbox));
    }

    private PaymentWebhookInbox newInbox(String payload, String type, Instant occurredAt) {
        var notification = new PaymentWebhookInbox.Notification(PaymentEnvironment.SANDBOX, "merchant", type,
                UUID.randomUUID().toString(), "b".repeat(64), payload, "transaction-uuid", "INV001", occurredAt);
        var value = PaymentWebhookInbox.receive(notification, createdAt);
        ReflectionTestUtils.setField(value, "id", UUID.randomUUID());
        return value;
    }

    private Claim claim(PaymentWebhookInbox value) {
        return new Claim(value.getId(), value.claim(Instant.now(), Duration.ofMinutes(5)));
    }

    private BookingPaymentSnapshot snapshot(boolean awaitingPayment, Instant deadline) {
        return new BookingPaymentSnapshot(bookingId, "BK-001", customerId, payment.getAmount(),
                payment.getCurrency(), deadline, awaitingPayment);
    }

    private String payload(String notificationType, String orderStatus, String transactionStatus,
            String amount, String currency) {
        return """
                {
                  "timestamp": 1757058220,
                  "notification_type": "%s",
                  "order": {
                    "id": "order-uuid", "order_id": "gateway-order", "order_status": "%s",
                    "order_currency": "%s", "order_amount": "%s", "order_invoice_number": "INV001"
                  },
                  "transaction": {
                    "id": "transaction-uuid", "transaction_id": "bank-reference",
                    "payment_method": "BANK_TRANSFER", "transaction_type": "PAYMENT",
                    "transaction_status": "%s", "transaction_amount": "%s",
                    "transaction_currency": "%s"
                  },
                  "customer": {"id": "customer-uuid", "customer_id": "%s"}
                }
                """.formatted(notificationType, orderStatus, currency, amount, transactionStatus,
                amount, currency, customerId);
    }
}
