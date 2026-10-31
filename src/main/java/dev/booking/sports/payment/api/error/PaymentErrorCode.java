package dev.booking.sports.payment.api.error;

import org.springframework.http.HttpStatus;
import dev.booking.sports.shared.exception.ErrorCode;

public enum PaymentErrorCode implements ErrorCode {
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Payment does not exist"),
    PAYMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "You cannot manage this payment"),
    PAYMENT_BOOKING_NOT_PAYABLE(HttpStatus.CONFLICT, "Booking is not eligible for payment"),
    PAYMENT_BOOKING_CHANGED(HttpStatus.CONFLICT, "Booking price, currency or deadline no longer matches the payment"),
    PAYMENT_EXPIRED(HttpStatus.CONFLICT, "Payment deadline has expired"),
    PAYMENT_ALREADY_SUCCEEDED(HttpStatus.CONFLICT, "Payment has already succeeded"),
    PAYMENT_CHECKOUT_NOT_ALLOWED(HttpStatus.CONFLICT, "Checkout is not allowed in the current state"),
    PAYMENT_ATTEMPT_NOT_FOUND(HttpStatus.NOT_FOUND, "Payment attempt does not exist"),
    PAYMENT_ATTEMPT_IN_PROGRESS(HttpStatus.CONFLICT, "Another payment attempt is still active"),
    PAYMENT_OUTCOME_UNKNOWN(HttpStatus.CONFLICT, "Payment outcome must be reconciled before continuing"),
    PAYMENT_METHOD_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "Payment method is not supported"),
    PAYMENT_CURRENCY_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "Only VND payments are supported"),
    PAYMENT_AMOUNT_INVALID(HttpStatus.BAD_REQUEST, "Payment amount must be positive whole dong"),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.CONFLICT, "Transaction amount does not match the payment"),
    PAYMENT_CURRENCY_MISMATCH(HttpStatus.CONFLICT, "Transaction currency does not match the payment"),
    PAYMENT_TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Payment transaction does not exist"),
    PAYMENT_TRANSACTION_NOT_APPLICABLE(HttpStatus.CONFLICT, "Transaction cannot settle this payment"),
    PAYMENT_IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "Idempotency key was used with a different request"),
    PAYMENT_IDEMPOTENCY_REQUEST_IN_PROGRESS(HttpStatus.CONFLICT, "A request with this idempotency key is in progress"),
    INVALID_PAYMENT_PAGINATION(HttpStatus.BAD_REQUEST, "Page must be non-negative and size must be between 1 and 100"),
    INVALID_PAYMENT_DATE_RANGE(HttpStatus.BAD_REQUEST, "Start time must not be after end time"),
    SEPAY_IPN_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "IPN authentication failed"),
    SEPAY_IPN_INVALID(HttpStatus.BAD_REQUEST, "IPN payload is invalid"),
    SEPAY_IPN_CONFLICT(HttpStatus.CONFLICT, "IPN identity conflicts with an existing notification"),
    SEPAY_GATEWAY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Payment gateway is temporarily unavailable"),
    SEPAY_GATEWAY_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "Payment gateway request failed"),
    SEPAY_GATEWAY_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "Payment gateway is not configured"),
    PAYMENT_RECONCILIATION_IN_PROGRESS(HttpStatus.CONFLICT, "Payment reconciliation is already in progress"),
    REFUND_NOT_FOUND(HttpStatus.NOT_FOUND, "Refund does not exist"),
    REFUND_ACCESS_DENIED(HttpStatus.FORBIDDEN, "You cannot manage this refund"),
    REFUND_TRANSACTION_NOT_ELIGIBLE(HttpStatus.CONFLICT, "Transaction is not eligible for a refund"),
    REFUND_AMOUNT_EXCEEDED(HttpStatus.CONFLICT, "Refund exceeds the remaining refundable amount"),
    REFUND_PROCESSING_NOT_ALLOWED(HttpStatus.CONFLICT, "Refund cannot enter processing in its current state"),
    REFUND_COMPLETION_NOT_ALLOWED(HttpStatus.CONFLICT, "Refund cannot complete in its current state"),
    REFUND_REJECTION_NOT_ALLOWED(HttpStatus.CONFLICT, "Refund cannot be rejected in its current state"),
    REFUND_CANCELLATION_NOT_ALLOWED(HttpStatus.CONFLICT, "Refund cannot be cancelled in its current state"),
    REFUND_EVIDENCE_INVALID(HttpStatus.BAD_REQUEST, "Refund evidence is not a valid accessible upload");

    private final HttpStatus status;
    private final String message;

    PaymentErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String defaultMessage() {
        return message;
    }
}
