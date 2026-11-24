package dev.booking.sports.payment.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.booking.sports.payment.api.dto.request.SePayIpnRequest;
import dev.booking.sports.payment.domain.enums.PaymentAttemptStatus;
import dev.booking.sports.payment.domain.enums.PaymentEnvironment;
import dev.booking.sports.payment.domain.model.PaymentAttempt;
import dev.booking.sports.payment.domain.repository.PaymentAttemptRepository;
import dev.booking.sports.payment.domain.repository.PaymentRepository;
import dev.booking.sports.payment.infrastructure.sepay.SePayOrderClient.Snapshot;
import dev.booking.sports.shared.exception.ApiException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import static dev.booking.sports.payment.api.error.PaymentErrorCode.*;

@Service
@RequiredArgsConstructor
public class PaymentReconciliationStore {
    private final PaymentRepository payments;
    private final PaymentAttemptRepository attempts;
    private final SePayIpnService inbox;
    private final Validator validator;

    @Transactional(readOnly = true)
    public Target target(UUID paymentId) {
        if (!payments.existsById(paymentId)) throw new ApiException(PAYMENT_NOT_FOUND);
        var attempt = attempts.findFirstByPaymentIdOrderByAttemptNumberDesc(paymentId)
                .orElseThrow(() -> new ApiException(PAYMENT_ATTEMPT_NOT_FOUND));
        return new Target(paymentId, attempt.getId(), attempt.getEnvironment(),
                attempt.getMerchantId(), attempt.getInvoiceNumber());
    }

    @Transactional
    public void apply(Target target, Optional<Snapshot> result) {
        var attempt = locked(target);
        Instant now = Instant.now();
        if (result.isEmpty()) {
            record(attempt, now, "GATEWAY_ORDER_NOT_FOUND");
            return;
        }
        var snapshot = result.get();
        var order = snapshot.order();
        if (!validator.validate(order).isEmpty() || !target.invoice().equals(order.orderInvoiceNumber())
                || attempt.getAmount().compareTo(order.orderAmount()) != 0
                || !attempt.getCurrency().equals(order.orderCurrency())
                || (attempt.getProviderOrderId() != null && !attempt.getProviderOrderId().equals(order.orderId()))
                || attempts.existsByProviderAndEnvironmentAndMerchantIdAndProviderOrderIdAndIdNot(
                    attempt.getProvider(), target.environment(), target.merchant(), order.orderId(), target.attemptId())) {
            throw new ApiException(SEPAY_GATEWAY_REQUEST_FAILED);
        }
        boolean hasCollection = false;
        for (var transaction : snapshot.transactions()) {
            if (!"PAYMENT".equals(transaction.transactionType())) continue;
            if (!validator.validate(transaction).isEmpty()) throw new ApiException(SEPAY_GATEWAY_REQUEST_FAILED);
            String type = "VOID".equals(transaction.transactionStatus()) ? "TRANSACTION_VOID" : "ORDER_PAID";
            inbox.receiveReconciliation(new SePayIpnRequest(now.getEpochSecond(), type, order, transaction, null));
            hasCollection |= "APPROVED".equals(transaction.transactionStatus());
        }
        String error = null;
        if ("CAPTURED".equals(order.orderStatus()) && hasCollection) {
            // Only the inbox worker may settle money and confirm the booking.
        } else if ("CANCELLED".equals(order.orderStatus()) && snapshot.transactions().isEmpty()
                && attempt.getStatus().isActive()) {
            attempt.recordGatewayOutcome(PaymentAttemptStatus.CANCELLED, order.orderId(), order.orderStatus());
        } else if ("AUTHENTICATION_NOT_NEEDED".equals(order.orderStatus()) && snapshot.transactions().isEmpty()
                && attempt.getStatus() != PaymentAttemptStatus.UNKNOWN && attempt.getStatus().isActive()) {
            attempt.markPending(order.orderId(), order.orderStatus());
        } else {
            error = "GATEWAY_STATE_REQUIRES_REVIEW";
            if (attempt.getStatus().isActive()) attempt.markUnknown(error, now.plusSeconds(60));
        }
        attempt.recordReconciliation(now, error == null ? null : now.plusSeconds(60), error);
    }

    @Transactional
    public void failure(Target target, String reason) {
        record(locked(target), Instant.now(), reason);
    }

    private void record(PaymentAttempt attempt, Instant now, String reason) {
        // A failed lookup is not financial evidence; preserve the existing checkout state.
        attempt.recordReconciliation(now, now.plusSeconds(60), reason);
    }

    private PaymentAttempt locked(Target target) {
        payments.findByIdForUpdate(target.paymentId()).orElseThrow(() -> new ApiException(PAYMENT_NOT_FOUND));
        return attempts.findInvoiceForUpdate(target.environment(), target.merchant(), target.invoice())
                .filter(a -> a.getId().equals(target.attemptId()))
                .orElseThrow(() -> new ApiException(PAYMENT_ATTEMPT_NOT_FOUND));
    }

    public record Target(UUID paymentId, UUID attemptId, PaymentEnvironment environment, String merchant, String invoice) {}
}
