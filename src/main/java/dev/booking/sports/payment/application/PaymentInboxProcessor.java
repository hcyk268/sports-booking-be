package dev.booking.sports.payment.application;

import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.booking.sports.booking.publicapi.BookingPaymentFacade;
import dev.booking.sports.payment.api.dto.request.SePayIpnRequest;
import dev.booking.sports.payment.config.PaymentInboxProperties;
import dev.booking.sports.payment.domain.enums.PaymentAttemptStatus;
import dev.booking.sports.payment.domain.enums.PaymentResolutionStatus;
import dev.booking.sports.payment.domain.enums.PaymentStatus;
import dev.booking.sports.payment.domain.event.PaymentOutboxMessageTypes;
import dev.booking.sports.payment.domain.event.PaymentReviewRequiredEvent;
import dev.booking.sports.payment.domain.event.PaymentSucceededEvent;
import dev.booking.sports.payment.domain.model.PaymentAttempt;
import dev.booking.sports.payment.domain.model.PaymentTransaction;
import dev.booking.sports.payment.domain.model.PaymentWebhookInbox;
import dev.booking.sports.payment.domain.repository.PaymentAttemptRepository;
import dev.booking.sports.payment.domain.repository.PaymentInboxRepository;
import dev.booking.sports.payment.domain.repository.PaymentRepository;
import dev.booking.sports.payment.domain.repository.PaymentTransactionRepository;
import dev.booking.sports.payment.domain.repository.PaymentInboxRepository.Claim;
import dev.booking.sports.shared.config.PaymentSettlementProperties;
import dev.booking.sports.shared.outbox.OutboxService;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class PaymentInboxProcessor {

    private final PaymentInboxRepository inboxes;
    private final PaymentAttemptRepository attempts;
    private final PaymentRepository payments;
    private final PaymentTransactionRepository transactions;
    private final BookingPaymentFacade bookings;
    private final OutboxService outbox;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final PaymentInboxProperties properties;
    private final PaymentSettlementProperties settlementProperties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(Claim claim) {
        var inbox = inboxes.findForUpdate(claim.id()).orElse(null);
        if (inbox == null || !inbox.ownsLiveLease(claim.token(), Instant.now())) {
            return;
        }
        if (inbox.getAttempts() > properties.maxAttempts()) {
            review(inbox, null, "RETRY_EXHAUSTED");
            return;
        }

        ParsedNotification parsed = parse(inbox);
        if (parsed == null) {
            review(inbox, null, "INVALID_NOTIFICATION");
            return;
        }

        SePayIpnRequest request = parsed.request();
        PaymentTransaction.Receipt facts = parsed.facts();
        var receipt = transactions.findByProviderAndEnvironmentAndMerchantIdAndProviderTransactionId(
                inbox.getProvider(), inbox.getEnvironment(), inbox.getMerchantId(), facts.transactionId())
                .orElse(null);
        if (receipt != null && !sameFacts(receipt, facts)) {
            review(inbox, receipt, "CONFLICTING_TRANSACTION");
            return;
        }
        if (receipt == null) {
            receipt = transactions.saveAndFlush(PaymentTransaction.record(facts, inbox.getReceivedAt()));
        }

        if ("TRANSACTION_VOID".equals(request.notificationType())) {
            boolean applied = receipt.getResolutionStatus() == PaymentResolutionStatus.APPLIED;
            receipt.recordProviderStatus(facts.status());
            pauseVoidedCheckout(inbox, receipt);
            review(inbox, receipt, applied ? "VOID_AFTER_SETTLEMENT" : "VOID_REQUIRES_RECONCILIATION");
            return;
        }

        if (!"ORDER_PAID".equals(request.notificationType())
                || !"CAPTURED".equals(request.order().orderStatus())
                || !"PAYMENT".equals(facts.type())
                || !"APPROVED".equals(facts.status())) {
            review(inbox, receipt, "UNEXPECTED_GATEWAY_STATE");
            return;
        }
        if (!Objects.equals(receipt.getProviderStatus(), facts.status())) {
            review(inbox, receipt, "OUT_OF_ORDER_NOTIFICATION");
            return;
        }
        if (request.order().orderAmount().compareTo(receipt.getAmount()) != 0
                || !request.order().orderCurrency().equals(receipt.getCurrency())) {
            review(inbox, receipt, "ORDER_TRANSACTION_MISMATCH");
            return;
        }
        if (receipt.getResolutionStatus() != PaymentResolutionStatus.PENDING) {
            inbox.markProcessed(claim.token(), Instant.now());
            return;
        }

        apply(inbox, request, receipt);
    }

    private ParsedNotification parse(PaymentWebhookInbox inbox) {
        try {
            SePayIpnRequest request = mapper.readValue(inbox.getPayload(), SePayIpnRequest.class);
            if (request == null || !validator.validate(request).isEmpty()
                    || !Objects.equals(request.notificationType(), inbox.getNotificationType())
                    || !Objects.equals(request.transaction().id(), inbox.getProviderTransactionId())
                    || !Objects.equals(request.order().orderInvoiceNumber(), inbox.getInvoiceNumber())) {
                return null;
            }
            var transaction = request.transaction();
            var facts = new PaymentTransaction.Receipt(inbox.getEnvironment(), inbox.getMerchantId(),
                    transaction.id(), transaction.transactionId(), request.order().orderId(),
                    request.order().orderInvoiceNumber(), transaction.transactionType(),
                    transaction.transactionStatus(), transaction.paymentMethod(), transaction.transactionAmount(),
                    transaction.transactionCurrency(), inbox.getProviderOccurredAt());
            return new ParsedNotification(request, facts);
        } catch (JacksonException | IllegalArgumentException exception) {
            return null;
        }
    }

    private void apply(PaymentWebhookInbox inbox, SePayIpnRequest request, PaymentTransaction receipt) {
        var paymentId = attempts.findPaymentIdByInvoice(
                inbox.getEnvironment(), inbox.getMerchantId(), inbox.getInvoiceNumber()).orElse(null);
        if (paymentId == null) {
            review(inbox, receipt, "UNMATCHED_TRANSACTION");
            return;
        }
        var bookingId = payments.findBookingIdById(paymentId).orElse(null);
        if (bookingId == null) {
            review(inbox, receipt, "PAYMENT_NOT_FOUND");
            return;
        }

        var booking = bookings.lockForPayment(bookingId).orElse(null);
        var payment = payments.findByIdForUpdate(paymentId).orElseThrow();
        var attempt = attempts.findInvoiceForUpdate(
                inbox.getEnvironment(), inbox.getMerchantId(), inbox.getInvoiceNumber()).orElseThrow();

        if (attempt.getProviderOrderId() != null
                && !attempt.getProviderOrderId().equals(receipt.getProviderOrderId())) {
            pause(attempt, "ORDER_ID_MISMATCH");
            review(inbox, receipt, "ORDER_ID_MISMATCH");
            return;
        }
        if (attempt.getProviderOrderId() == null
                && attempts.existsByProviderAndEnvironmentAndMerchantIdAndProviderOrderIdAndIdNot(
                        inbox.getProvider(), inbox.getEnvironment(), inbox.getMerchantId(),
                        receipt.getProviderOrderId(), attempt.getId())) {
            pause(attempt, "ORDER_ALREADY_BOUND");
            review(inbox, receipt, "ORDER_ALREADY_BOUND");
            return;
        }

        receipt.match(attempt);
        // The settlement FK references (receipt.id, receipt.paymentId).
        transactions.flush();

        if (receipt.getAmount().compareTo(attempt.getAmount()) != 0
                || receipt.getAmount().compareTo(payment.getAmount()) != 0) {
            pause(attempt, "AMOUNT_MISMATCH");
            review(inbox, receipt, "AMOUNT_MISMATCH");
            return;
        }
        if (!receipt.getCurrency().equals(attempt.getCurrency())
                || !receipt.getCurrency().equals(payment.getCurrency())) {
            pause(attempt, "CURRENCY_MISMATCH");
            review(inbox, receipt, "CURRENCY_MISMATCH");
            return;
        }
        if (attempt.getPaymentMethod() != null && receipt.getPaymentMethod() != null
                && !attempt.getPaymentMethod().equals(receipt.getPaymentMethod())) {
            pause(attempt, "PAYMENT_METHOD_MISMATCH");
            review(inbox, receipt, "PAYMENT_METHOD_MISMATCH");
            return;
        }
        if (booking == null) {
            review(inbox, receipt, "BOOKING_NOT_FOUND");
            return;
        }
        if (request.customer() != null && request.customer().customerId() != null
                && !booking.customerId().toString().equals(request.customer().customerId())) {
            pause(attempt, "CUSTOMER_MISMATCH");
            review(inbox, receipt, "CUSTOMER_MISMATCH");
            return;
        }

        attempt.recordGatewayOutcome(PaymentAttemptStatus.SUCCEEDED,
                receipt.getProviderOrderId(), request.order().orderStatus());
        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            review(inbox, receipt, "EXTRA_PAYMENT");
            return;
        }

        Instant deadline = earliest(payment.getExpiresAt(), attempt.getExpiresAt(), booking.holdExpiresAt());
        Instant paidAt = inbox.getProviderOccurredAt();
        if (paidAt == null) {
            if (!inbox.getReceivedAt().isBefore(deadline)) {
                review(inbox, receipt, "PAYMENT_TIME_UNKNOWN");
                return;
            }
            paidAt = inbox.getReceivedAt();
        } else if (!paidAt.isBefore(deadline)) {
            review(inbox, receipt, "LATE_PAYMENT");
            return;
        }

        Instant now = Instant.now();
        if (payment.getStatus() != PaymentStatus.PENDING
                || !booking.awaitingPayment()
                || !now.isBefore(deadline.plus(settlementProperties.grace()))) {
            review(inbox, receipt, "ON_TIME_PAYMENT_UNFULFILLABLE");
            return;
        }
        if (booking.totalAmount().compareTo(payment.getAmount()) != 0
                || !booking.currency().equals(payment.getCurrency())
                || !booking.holdExpiresAt().equals(payment.getExpiresAt())
                || !attempt.getExpiresAt().equals(payment.getExpiresAt())) {
            review(inbox, receipt, "BOOKING_CHANGED");
            return;
        }
        if (!bookings.confirmPayment(bookingId, paidAt)) {
            review(inbox, receipt, "BOOKING_NOT_CONFIRMABLE");
            return;
        }

        payment.settle(receipt, paidAt);
        outbox.enqueue("PAYMENT", paymentId, PaymentOutboxMessageTypes.SUCCEEDED,
                PaymentOutboxMessageTypes.VERSION,
                new PaymentSucceededEvent(paymentId, bookingId, attempt.getId(), receipt.getId(),
                        payment.getAmount(), payment.getCurrency(), payment.getSettledAt()));
        inbox.markProcessed(inbox.getLockToken(), Instant.now());
    }

    private boolean sameFacts(PaymentTransaction receipt, PaymentTransaction.Receipt facts) {
        return Objects.equals(receipt.getInvoiceNumber(), facts.invoiceNumber())
                && Objects.equals(receipt.getProviderOrderId(), facts.orderId())
                && Objects.equals(receipt.getProviderTransactionReference(), facts.reference())
                && Objects.equals(receipt.getTransactionType(), facts.type())
                && Objects.equals(receipt.getPaymentMethod(), facts.method())
                && receipt.getAmount().compareTo(facts.amount()) == 0
                && receipt.getCurrency().equals(facts.currency());
    }

    private void pauseVoidedCheckout(PaymentWebhookInbox inbox, PaymentTransaction receipt) {
        var paymentId = attempts.findPaymentIdByInvoice(
                inbox.getEnvironment(), inbox.getMerchantId(), inbox.getInvoiceNumber()).orElse(null);
        if (paymentId == null) {
            return;
        }
        var bookingId = payments.findBookingIdById(paymentId).orElse(null);
        if (bookingId == null) {
            return;
        }
        bookings.lockForPayment(bookingId);
        payments.findByIdForUpdate(paymentId).orElseThrow();
        var attempt = attempts.findInvoiceForUpdate(
                inbox.getEnvironment(), inbox.getMerchantId(), inbox.getInvoiceNumber()).orElseThrow();
        if (attempt.getProviderOrderId() == null
                || attempt.getProviderOrderId().equals(receipt.getProviderOrderId())) {
            receipt.match(attempt);
            transactions.flush();
        }
        pause(attempt, "VOID_REQUIRES_RECONCILIATION");
    }

    private void pause(PaymentAttempt attempt, String reason) {
        if (attempt.getStatus().isActive()) {
            attempt.markUnknown(reason, Instant.now().plus(properties.retryDelay()));
        }
    }

    private void review(PaymentWebhookInbox inbox, PaymentTransaction receipt, String reason) {
        boolean changed = receipt == null
                || receipt.getResolutionStatus() != PaymentResolutionStatus.REVIEW_REQUIRED
                || !reason.equals(receipt.getResolutionReason());
        if (receipt != null) {
            receipt.requireReview(reason);
        }
        inbox.requireReview(inbox.getLockToken(), Instant.now(), reason);
        if (changed) {
            outbox.enqueue("PAYMENT_INBOX", inbox.getId(), PaymentOutboxMessageTypes.REVIEW_REQUIRED,
                    PaymentOutboxMessageTypes.VERSION,
                    new PaymentReviewRequiredEvent(inbox.getId(), receipt == null ? null : receipt.getPaymentId(),
                            receipt == null ? null : receipt.getId(), inbox.getInvoiceNumber(), reason, Instant.now()));
        }
    }

    private static Instant earliest(Instant first, Instant second, Instant third) {
        Instant result = Objects.requireNonNull(first, "payment deadline must not be null");
        if (second != null && second.isBefore(result)) {
            result = second;
        }
        if (third != null && third.isBefore(result)) {
            result = third;
        }
        return result;
    }

    private record ParsedNotification(SePayIpnRequest request, PaymentTransaction.Receipt facts) {}
}
