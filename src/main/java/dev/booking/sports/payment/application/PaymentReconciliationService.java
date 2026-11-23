package dev.booking.sports.payment.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import dev.booking.sports.payment.api.dto.response.PaymentReconciliationResponse;
import dev.booking.sports.payment.infrastructure.redis.PaymentLockManager;
import dev.booking.sports.payment.infrastructure.sepay.SePayOrderClient;
import dev.booking.sports.shared.exception.ApiException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentReconciliationService {
    private final PaymentReconciliationStore store;
    private final SePayOrderClient gateway;
    private final PaymentLockManager locks;

    public PaymentReconciliationResponse reconcile(UUID paymentId) {
        // No database transaction or row lock is held during the external request.
        try (var ignored = locks.acquireReconciliation(paymentId)) {
            var target = store.target(paymentId);
            Instant requestedAt = Instant.now();
            try {
                var result = gateway.find(target.environment(), target.merchant(), target.invoice());
                store.apply(target, result);
            } catch (ApiException exception) {
                store.failure(target, exception.getErrorCode().code());
                throw exception;
            }
            return new PaymentReconciliationResponse(paymentId, target.attemptId(), requestedAt);
        }
    }
}
