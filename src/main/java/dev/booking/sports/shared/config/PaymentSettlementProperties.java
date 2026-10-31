package dev.booking.sports.shared.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payment.settlement")
public record PaymentSettlementProperties(Duration grace) {
    public PaymentSettlementProperties {
        grace = grace == null ? Duration.ofMinutes(2) : grace;
        if (grace.isNegative() || grace.compareTo(Duration.ofMinutes(10)) > 0) {
            throw new IllegalArgumentException("Payment settlement grace must be between 0 and 10 minutes");
        }
    }
}
