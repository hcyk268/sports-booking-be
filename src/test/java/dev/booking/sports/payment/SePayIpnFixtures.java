package dev.booking.sports.payment;

public final class SePayIpnFixtures {
    private SePayIpnFixtures() {}

    public static final String SECRET = "test-secret";
    public static final String PAYLOAD = """
            {
              "timestamp": 1757058220, "notification_type": "ORDER_PAID",
              "order": {
                "id": "order-uuid", "order_id": "gateway-order", "order_status": "CAPTURED",
                "order_currency": "VND", "order_amount": "50000.00", "order_invoice_number": "INV001",
                "custom_data": {"future_field": true}
              },
              "transaction": {
                "id": "transaction-uuid", "transaction_id": "bank-reference", "payment_method": "BANK_TRANSFER",
                "transaction_type": "PAYMENT", "transaction_status": "APPROVED",
                "transaction_amount": "50000", "transaction_currency": "VND"
              },
              "customer": {"id": "customer-uuid", "customer_id": "customer-reference"},
              "future_metadata": {"keep": true}
            }
            """;
}
