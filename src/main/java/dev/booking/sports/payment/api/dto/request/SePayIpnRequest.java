package dev.booking.sports.payment.api.dto.request;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SePayIpnRequest(
        @NotNull @Positive Long timestamp,
        @JsonProperty("notification_type") @NotBlank @Size(max = 100) String notificationType,
        @NotNull @Valid Order order,
        @NotNull @Valid Transaction transaction,
        @Valid Customer customer) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Order(
            @NotBlank @Size(max = 150) String id,
            @JsonProperty("order_id") @NotBlank @Size(max = 150) String orderId,
            @JsonProperty("order_status") @NotBlank @Size(max = 100) String orderStatus,
            @JsonProperty("order_currency") @NotBlank @Size(min = 3, max = 3) String orderCurrency,
            @JsonProperty("order_amount") @NotNull @Positive BigDecimal orderAmount,
            @JsonProperty("order_invoice_number") @NotBlank @Size(max = 100) String orderInvoiceNumber,
            @JsonProperty("order_description") String orderDescription,
            @JsonProperty("custom_data") JsonNode customData,
            @JsonProperty("user_agent") String userAgent,
            @JsonProperty("ip_address") String ipAddress) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Transaction(
            @NotBlank @Size(max = 150) String id,
            @JsonProperty("transaction_id") @Size(max = 150) String transactionId,
            @JsonProperty("payment_method") @Size(max = 50) String paymentMethod,
            @JsonProperty("transaction_type") @NotBlank @Size(max = 50) String transactionType,
            @JsonProperty("transaction_status") @NotBlank @Size(max = 100) String transactionStatus,
            @JsonProperty("transaction_amount") @NotNull @Positive BigDecimal transactionAmount,
            @JsonProperty("transaction_currency") @NotBlank @Size(min = 3, max = 3) String transactionCurrency,
            @JsonProperty("transaction_date") String transactionDate,
            @JsonProperty("authentication_status") String authenticationStatus,
            @JsonProperty("card_number") String cardNumber,
            @JsonProperty("card_holder_name") String cardHolderName,
            @JsonProperty("card_expiry") String cardExpiry,
            @JsonProperty("card_funding_method") String cardFundingMethod,
            @JsonProperty("card_brand") String cardBrand) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Customer(String id, @JsonProperty("customer_id") String customerId) {
    }
}
