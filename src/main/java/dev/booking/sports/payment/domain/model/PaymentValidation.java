package dev.booking.sports.payment.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

final class PaymentValidation {

    private PaymentValidation() {
    }

    static String text(String value, String name, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(name + " must contain 1-" + maxLength + " characters");
        }
        return value;
    }

    static String optionalText(String value, String name, int maxLength) {
        return value == null ? null : text(value, name, maxLength);
    }

    static String hash(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Request hash must be a lowercase SHA-256 hash");
        }
        return value;
    }

    static BigDecimal amount(BigDecimal value) {
        Objects.requireNonNull(value, "amount must not be null");
        if (value.signum() <= 0 || value.compareTo(new BigDecimal("9999999999999.99")) > 0
                || value.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("amount must be positive and fit NUMERIC(15,2)");
        }
        return value;
    }

    static BigDecimal vndAmount(BigDecimal value) {
        amount(value);
        if (value.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("VND amount must be whole dong");
        }
        return value;
    }

    static String currency(String value) {
        if (value == null || !value.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be three uppercase letters");
        }
        return value;
    }

    static String vnd(String value) {
        if (!"VND".equals(value)) {
            throw new IllegalArgumentException("Only VND is supported");
        }
        return value;
    }
}
