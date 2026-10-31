package dev.booking.sports.payment.application;

import dev.booking.sports.payment.api.dto.request.CreatePaymentRequest;
import dev.booking.sports.payment.api.dto.response.PaymentCheckoutResponse;
import dev.booking.sports.payment.api.error.PaymentErrorCode;
import dev.booking.sports.payment.infrastructure.redis.PaymentLockManager;
import dev.booking.sports.shared.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class BookingPaymentService {

    private final PaymentLockManager lockManager;
    private final PaymentCreationTransaction creationTransaction;

    public PaymentCheckoutResponse createPayment(UUID customerId, UUID bookingId,
                                                 String idempotencyKey, CreatePaymentRequest request) {
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(bookingId, "bookingId must not be null");
        Objects.requireNonNull(request, "request must not be null");
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new IllegalArgumentException("idempotencyKey must contain 1-100 characters");
        }
        String method = request.paymentMethod();
        if (method != null && !method.matches("BANK_TRANSFER|NAPAS_BANK_TRANSFER")) {
            throw new ApiException(PaymentErrorCode.PAYMENT_METHOD_NOT_SUPPORTED);
        }
        String requestHash = requestHash(bookingId, method);
        try (var lock = lockManager.acquireIdempotency(customerId, sha256(idempotencyKey))) {
            return creationTransaction.create(customerId, bookingId, idempotencyKey, requestHash, method);
        }
    }

    static String requestHash(UUID bookingId, String method) {
        String input = "bookingId=" + bookingId + "\npaymentMethod=" + (method == null ? "<SELECT_AT_GATEWAY>" : method);
        return sha256(input);
    }

    private static String sha256(String input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
