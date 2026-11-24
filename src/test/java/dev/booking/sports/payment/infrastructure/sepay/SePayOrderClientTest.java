package dev.booking.sports.payment.infrastructure.sepay;

import static org.assertj.core.api.Assertions.*;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import dev.booking.sports.payment.config.SePayProperties;
import dev.booking.sports.payment.domain.enums.PaymentEnvironment;
import dev.booking.sports.shared.exception.ApiException;
import tools.jackson.databind.json.JsonMapper;

class SePayOrderClientTest {
    HttpServer server;
    SePayOrderClient client;
    int status = 200;
    String body = """
            {"data":{"id":"uuid","order_id":"order","order_invoice_number":"INV /001",
            "order_status":"CAPTURED","order_currency":"VND","order_amount":"50000",
            "transactions":[{"id":"tx","transaction_type":"PAYMENT","transaction_status":"APPROVED",
            "transaction_amount":"50000","transaction_currency":"VND","payment_method":"BANK_TRANSFER",
            "transaction_date":"2026-11-23 10:00:00","card_holder_name":"PRIVATE"}]}}
            """;
    AtomicReference<String> path = new AtomicReference<>();
    AtomicReference<String> auth = new AtomicReference<>();
    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange -> {
            path.set(exchange.getRequestURI().getRawPath()); auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            if (status == 302) exchange.getResponseHeaders().add("Location","http://127.0.0.1:1/credential-leak");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status,bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        client = new SePayOrderClient(new SePayProperties(true,PaymentEnvironment.SANDBOX,"merchant","test-secret",null,null,null),
                JsonMapper.builder().build(), HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()),Duration.ofSeconds(2));
    }
    @AfterEach void stop() { server.stop(0); }
    @Test void queriesEncodedInvoiceWithBasicAuthAndRemovesCardDetails() {
        var response = client.find(PaymentEnvironment.SANDBOX,"merchant","INV /001").orElseThrow();
        assertThat(path.get()).isEqualTo("/v1/order/detail/INV%20%2F001");
        assertThat(auth.get()).isEqualTo("Basic " + Base64.getEncoder().encodeToString("merchant:test-secret".getBytes(StandardCharsets.UTF_8)));
        assertThat(response.transactions().getFirst().cardHolderName()).isNull();
        assertThat(response.order().orderInvoiceNumber()).isEqualTo("INV /001");
    }
    @Test void notFoundIsNotAConfirmedCancellation() {
        status=404;
        assertThat(client.find(PaymentEnvironment.SANDBOX,"merchant","INV")).isEmpty();
    }
    @Test void errorsAndRedirectsFailClosedWithoutExposingResponse() {
        for (int code : new int[]{401,403,302,429,500}) {
            status=code; body="sensitive-provider-body";
            assertThatThrownBy(() -> client.find(PaymentEnvironment.SANDBOX,"merchant","INV"))
                    .isInstanceOf(ApiException.class).hasMessageNotContaining("sensitive-provider-body");
        }
    }
    @Test void malformedOrIncompleteResponsesAreRejected() {
        for(String invalid : new String[]{"not-json","{}","null","{\"data\":null}","{\"data\":{\"transactions\":null}}"}) {
            body=invalid;
            assertThatThrownBy(() -> client.find(PaymentEnvironment.SANDBOX,"merchant","INV")).isInstanceOf(ApiException.class);
        }
    }
    @Test void mismatchedMerchantAndEnvironmentNeverSendCredentials() {
        assertThatThrownBy(() -> client.find(PaymentEnvironment.PRODUCTION,"merchant","INV")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> client.find(PaymentEnvironment.SANDBOX,"other","INV")).isInstanceOf(ApiException.class);
        assertThat(path.get()).isNull();
    }
}
