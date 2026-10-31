package dev.booking.sports.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import dev.booking.sports.payment.domain.enums.PaymentEnvironment;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.time.ZoneId;

@ConfigurationProperties(prefix = "app.payment.sepay")
public record SePayProperties(boolean enabled, PaymentEnvironment environment, String merchantId,
        String secretKey, String successUrl, String errorUrl, String cancelUrl, ZoneId transactionZoneId) {

    @ConstructorBinding
    public SePayProperties {
        environment = environment == null ? PaymentEnvironment.SANDBOX : environment;
        transactionZoneId = transactionZoneId == null ? ZoneId.of("Asia/Ho_Chi_Minh") : transactionZoneId;
    }

    public SePayProperties(boolean enabled, PaymentEnvironment environment, String merchantId,
            String secretKey, String successUrl, String errorUrl, String cancelUrl) {
        this(enabled, environment, merchantId, secretKey, successUrl, errorUrl, cancelUrl, null);
    }

    @Override
    public String toString() {
        return "SePayProperties[enabled=" + enabled + ", environment=" + environment + ", credentials=<redacted>]";
    }
}
