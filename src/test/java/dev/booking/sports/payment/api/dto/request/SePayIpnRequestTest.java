package dev.booking.sports.payment.api.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import jakarta.validation.Validation;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class SePayIpnRequestTest {
    private final ObjectMapper mapper = new ObjectMapper();
    // Field names and wire types from https://developer.sepay.vn/vi/cong-thanh-toan/IPN
    private static final String PAYLOAD = """
            {
              "timestamp": 1757058220,
              "notification_type": "ORDER_PAID",
              "order": {
                "id": "e2c195be-c721-47eb-b323-99ab24e52d85",
                "order_id": "NPSETVI00101000042R",
                "order_status": "CAPTURED",
                "order_currency": "VND",
                "order_amount": "50000.00",
                "order_invoice_number": "SUB_202509_001",
                "custom_data": [],
                "user_agent": "Mozilla/5.0",
                "ip_address": "192.0.2.1",
                "order_description": "Thanh toán đặt sân"
              },
              "transaction": {
                "id": "384c66dd-41e6-4316-a544-b4141682595c",
                "transaction_id": "68ba94ac80123",
                "payment_method": "CARD",
                "transaction_type": "PAYMENT",
                "transaction_date": "2025-09-01 00:00:15",
                "transaction_status": "APPROVED",
                "transaction_amount": "50000",
                "transaction_currency": "VND",
                "authentication_status": "AUTHENTICATION_SUCCESSFUL",
                "card_number": "4111XXXXXXXX1111",
                "card_holder_name": "NGUYEN VAN A",
                "card_expiry": "12/26",
                "card_funding_method": "CREDIT",
                "card_brand": "VISA"
              },
              "customer": {"id": "gateway-customer", "customer_id": "CUST_001"}
            }
            """;

    @Test
    void acceptsDocumentedPayloadAndMapsAdditionalFieldsAndStringAmounts() {
        var request = mapper.readValue(PAYLOAD, SePayIpnRequest.class);
        assertValid(request);
        assertThat(request.order().orderAmount()).isEqualByComparingTo("50000.00");
        assertThat(request.transaction().transactionAmount()).isEqualByComparingTo("50000");
        assertThat(request.order().customData().isArray()).isTrue();
        assertThat(request.order().userAgent()).isEqualTo("Mozilla/5.0");
        assertThat(request.order().ipAddress()).isEqualTo("192.0.2.1");
        assertThat(request.transaction().cardNumber()).isEqualTo("4111XXXXXXXX1111");
        assertThat(request.transaction().cardHolderName()).isEqualTo("NGUYEN VAN A");
        assertThat(request.transaction().cardExpiry()).isEqualTo("12/26");
        assertThat(request.transaction().cardFundingMethod()).isEqualTo("CREDIT");
        assertThat(request.transaction().cardBrand()).isEqualTo("VISA");
        assertThat(request.transaction().transactionDate()).isEqualTo("2025-09-01 00:00:15");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void acceptsMissingOrNullCustomerForBankTransfers(boolean explicitNull) {
        var payload = (ObjectNode) mapper.readTree(PAYLOAD);
        if (explicitNull) {
            payload.putNull("customer");
        } else {
            payload.remove("customer");
        }
        var request = mapper.treeToValue(payload, SePayIpnRequest.class);
        assertValid(request);
        assertThat(request.customer()).isNull();
    }

    @Test
    void bankTransferDoesNotRequireCardFieldsOrOptionalOrderMetadata() {
        var payload = (ObjectNode) mapper.readTree(PAYLOAD);
        var order = (ObjectNode) payload.get("order");
        order.remove("custom_data");
        order.remove("user_agent");
        order.remove("ip_address");
        var transaction = (ObjectNode) payload.get("transaction");
        transaction.put("payment_method", "BANK_TRANSFER");
        for (var field : new String[] {"card_number", "card_holder_name", "card_expiry", "card_funding_method", "card_brand"}) {
            transaction.remove(field);
        }
        var request = mapper.treeToValue(payload, SePayIpnRequest.class);
        assertValid(request);
        assertThat(request.transaction().cardNumber()).isNull();
        assertThat(request.order().customData()).isNull();
    }

    @Test
    void customDataCanRetainStructuredJsonWithoutAssumingAnArraySchema() {
        var payload = (ObjectNode) mapper.readTree(PAYLOAD);
        ((ObjectNode) payload.get("order")).set("custom_data", mapper.readTree("{\"booking\": {\"reference\": \"BK001\"}}"));
        var request = mapper.treeToValue(payload, SePayIpnRequest.class);
        assertThat(request.order().customData().path("booking").path("reference").asText()).isEqualTo("BK001");
    }

    private void assertValid(SePayIpnRequest request) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request)).isEmpty();
        }
    }
}
