package dev.booking.sports.payment.infrastructure.sepay;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import dev.booking.sports.payment.api.dto.request.SePayIpnRequest;
import dev.booking.sports.payment.config.SePayProperties;
import dev.booking.sports.payment.domain.enums.PaymentEnvironment;
import dev.booking.sports.shared.exception.ApiException;
import tools.jackson.databind.ObjectMapper;
import static dev.booking.sports.payment.api.error.PaymentErrorCode.*;

@Component
public class SePayOrderClient {
    private final SePayProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final URI base;
    private final Duration timeout;

    @Autowired
    public SePayOrderClient(SePayProperties properties, ObjectMapper mapper) {
        this(properties, mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build(),
                URI.create(properties.environment() == PaymentEnvironment.SANDBOX
                        ? "https://pgapi-sandbox.sepay.vn" : "https://pgapi.sepay.vn"), Duration.ofSeconds(10));
    }

    // Package-private transport seam: production hosts cannot be overridden by API input.
    SePayOrderClient(SePayProperties properties, ObjectMapper mapper, HttpClient client, URI base, Duration timeout) {
        this.properties = properties;
        this.mapper = mapper;
        this.client = client;
        this.base = base;
        this.timeout = timeout;
    }

    public Optional<Snapshot> find(PaymentEnvironment environment, String merchant, String invoice) {
        if (!properties.enabled() || environment != properties.environment()
                || merchant == null || !merchant.equals(properties.merchantId())
                || properties.secretKey() == null || properties.secretKey().isBlank()) {
            throw new ApiException(SEPAY_GATEWAY_NOT_CONFIGURED);
        }
        String authorization = Base64.getEncoder().encodeToString(
                (merchant + ":" + properties.secretKey()).getBytes(StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(URI.create(base + "/v1/order/detail/"
                        + UriUtils.encodePathSegment(invoice, StandardCharsets.UTF_8)))
                .timeout(timeout).header("Authorization", "Basic " + authorization)
                .header("Accept", "application/json").GET().build();
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 404) return Optional.empty();
            if (response.statusCode() == 429 || response.statusCode() >= 500)
                throw new ApiException(SEPAY_GATEWAY_UNAVAILABLE);
            if (response.statusCode() != 200) throw new ApiException(SEPAY_GATEWAY_REQUEST_FAILED);
            var data = mapper.readTree(response.body()).path("data");
            if (!data.isObject() || !data.path("transactions").isArray())
                throw new ApiException(SEPAY_GATEWAY_REQUEST_FAILED);
            var order = mapper.treeToValue(data, SePayIpnRequest.Order.class);
            var transactions = new java.util.ArrayList<SePayIpnRequest.Transaction>();
            for (var item : data.path("transactions")) {
                var tx = mapper.treeToValue(item, SePayIpnRequest.Transaction.class);
                if (tx == null) throw new ApiException(SEPAY_GATEWAY_REQUEST_FAILED);
                // Retain financial facts only, never cardholder details.
                transactions.add(new SePayIpnRequest.Transaction(tx.id(), tx.transactionId(), tx.paymentMethod(),
                        tx.transactionType(), tx.transactionStatus(), tx.transactionAmount(), tx.transactionCurrency(),
                        tx.transactionDate(), null, null, null, null, null, null));
            }
            var safeOrder = new SePayIpnRequest.Order(order.id(), order.orderId(), order.orderStatus(),
                    order.orderCurrency(), order.orderAmount(), order.orderInvoiceNumber(), null, null, null, null);
            return Optional.of(new Snapshot(safeOrder, List.copyOf(transactions)));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(SEPAY_GATEWAY_UNAVAILABLE);
        } catch (IOException exception) {
            throw new ApiException(SEPAY_GATEWAY_UNAVAILABLE);
        } catch (tools.jackson.core.JacksonException | IllegalArgumentException exception) {
            throw new ApiException(SEPAY_GATEWAY_REQUEST_FAILED);
        }
    }

    public record Snapshot(SePayIpnRequest.Order order, List<SePayIpnRequest.Transaction> transactions) {}
}
