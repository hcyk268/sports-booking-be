package dev.booking.sports.payment.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import dev.booking.sports.payment.api.dto.request.SePayIpnRequest;
import dev.booking.sports.payment.api.error.PaymentErrorCode;
import dev.booking.sports.payment.config.SePayProperties;
import dev.booking.sports.payment.domain.model.PaymentWebhookInbox;
import dev.booking.sports.payment.domain.model.PaymentWebhookInbox.Notification;
import dev.booking.sports.payment.domain.enums.PaymentProvider;
import dev.booking.sports.payment.domain.repository.PaymentInboxRepository;
import dev.booking.sports.payment.infrastructure.sepay.SePayTransactionTime;
import dev.booking.sports.shared.exception.ApiException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.DeserializationFeature;

@Service
@RequiredArgsConstructor
public class SePayIpnService {

    private final SePayProperties properties;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final PaymentInboxRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void receive(String secret, String payload) {
        authenticate(secret);
        SePayIpnRequest request = parse(payload);
        var order = request.order();
        var transaction = request.transaction();
        Instant occurredAt;
        boolean invalidTransactionTime = false;
        try {
            occurredAt = SePayTransactionTime.parse(transaction.transactionDate(), properties.transactionZoneId());
        } catch (DateTimeParseException exception) {
            occurredAt = null;
            invalidTransactionTime = true;
        }
        String key = hash(mapper.writeValueAsString(List.of(request.notificationType(), transaction.id(),
                order.orderStatus(), transaction.transactionStatus())));
        String payloadHash = hash(mapper.writeValueAsString(Arrays.asList(
                order.id(), order.orderId(), order.orderStatus(), order.orderCurrency(),
                order.orderAmount().stripTrailingZeros().toPlainString(), order.orderInvoiceNumber(),
                transaction.id(), transaction.transactionId(), transaction.transactionType(),
                transaction.transactionStatus(), transaction.transactionCurrency(), transaction.transactionDate(),
                transaction.transactionAmount().stripTrailingZeros().toPlainString(), transaction.paymentMethod(),
                request.customer() == null ? null : request.customer().id(),
                request.customer() == null ? null : request.customer().customerId())));
        Instant now = Instant.now();
        var notification = notification(request, key, payloadHash, payload, occurredAt);
        var inbox = invalidTransactionTime
                ? PaymentWebhookInbox.receiveForReview(notification, now, "INVALID_TRANSACTION_TIME")
                : switch (request.notificationType()) {
            case "ORDER_PAID", "TRANSACTION_VOID" -> PaymentWebhookInbox.receive(notification, now);
            default -> PaymentWebhookInbox.receiveForReview(notification, now, "Unsupported notification type");
        };
        var existing = repository.findByProviderAndEnvironmentAndMerchantIdAndDeduplicationKey(
                PaymentProvider.SEPAY, properties.environment(), properties.merchantId(), key);
        if (existing.isEmpty()) {
            repository.save(inbox);
            return;
        }
        if (!existing.get().hasPayloadHash(payloadHash)) {
            var conflict = notification(request, key + ":" + payloadHash, payloadHash, payload, occurredAt);
            repository.save(PaymentWebhookInbox.receiveForReview(conflict, now,
                    "Conflicting financial content for notification " + key));
        }
    }

    private void authenticate(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new ApiException(PaymentErrorCode.SEPAY_IPN_UNAUTHORIZED);
        }
        String expected = properties.secretKey();
        if (expected == null || expected.isBlank()) {
            throw new ApiException(PaymentErrorCode.SEPAY_GATEWAY_NOT_CONFIGURED);
        }
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), secret.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(PaymentErrorCode.SEPAY_IPN_UNAUTHORIZED);
        }
    }

    private SePayIpnRequest parse(String payload) {
        try {
            SePayIpnRequest request = mapper.readerFor(SePayIpnRequest.class)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(payload);
            if (request == null || !validator.validate(request).isEmpty()) {
                throw new ApiException(PaymentErrorCode.SEPAY_IPN_INVALID);
            }
            return request;
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new ApiException(PaymentErrorCode.SEPAY_IPN_INVALID);
        }
    }

    private Notification notification(SePayIpnRequest request, String key, String payloadHash, String payload,
            Instant occurredAt) {
        return new Notification(properties.environment(), properties.merchantId(), request.notificationType(),
                key, payloadHash, payload, request.transaction().id(), request.order().orderInvoiceNumber(), occurredAt);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
