package dev.booking.sports.payment.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import dev.booking.sports.payment.domain.enums.PaymentInboxStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import dev.booking.sports.payment.domain.enums.PaymentEnvironment;
import dev.booking.sports.payment.domain.enums.PaymentProvider;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Entity
@Table(name = "payment_webhook_inbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentWebhookInbox {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private PaymentEnvironment environment;

    @Column(name = "merchant_id", nullable = false, length = 100, updatable = false)
    private String merchantId;

    @Column(name = "notification_type", nullable = false, length = 100, updatable = false)
    private String notificationType;

    @Column(name = "deduplication_key", nullable = false, length = 255, updatable = false)
    private String deduplicationKey;

    @Column(name = "payload_hash", nullable = false, length = 64, updatable = false)
    private String payloadHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private String payload;

    @Column(name = "provider_transaction_id", length = 150, updatable = false)
    private String providerTransactionId;

    @Column(name = "invoice_number", length = 100, updatable = false)
    private String invoiceNumber;

    @Column(name = "provider_occurred_at", updatable = false)
    private Instant providerOccurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentInboxStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "lock_token")
    private UUID lockToken;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static PaymentWebhookInbox receive(Notification notification, Instant now) {
        Objects.requireNonNull(notification, "notification must not be null");
        Objects.requireNonNull(now, "now must not be null");
        PaymentWebhookInbox inbox = new PaymentWebhookInbox();
        inbox.provider = PaymentProvider.SEPAY;
        inbox.environment = notification.environment();
        inbox.merchantId = notification.merchantId();
        inbox.notificationType = notification.type();
        inbox.deduplicationKey = notification.deduplicationKey();
        inbox.payloadHash = notification.payloadHash();
        inbox.payload = notification.payload();
        inbox.providerTransactionId = notification.transactionId();
        inbox.invoiceNumber = notification.invoiceNumber();
        inbox.providerOccurredAt = notification.occurredAt();
        inbox.status = PaymentInboxStatus.PENDING;
        inbox.receivedAt = now;
        inbox.availableAt = now;
        return inbox;
    }

    public boolean hasPayloadHash(String hash) {
        return payloadHash.equals(hash);
    }

    public static PaymentWebhookInbox receiveForReview(Notification notification, Instant now, String reason) {
        var inbox = receive(notification, now);
        inbox.lastError = text(reason, "reason", 2000);
        inbox.status = PaymentInboxStatus.REVIEW_REQUIRED;
        return inbox;
    }

    public UUID claim(Instant now, Duration leaseDuration) {
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(leaseDuration, "leaseDuration must not be null");
        Instant deadline = now.plus(leaseDuration);
        if (leaseDuration.isNegative() || !deadline.isAfter(now)
                || leaseDuration.compareTo(Duration.ofNanos(1000)) < 0) {
            throw new IllegalArgumentException("Lease must last at least one database microsecond");
        }
        boolean ready = status == PaymentInboxStatus.PENDING && !now.isBefore(availableAt);
        boolean abandoned = status == PaymentInboxStatus.PROCESSING && !now.isBefore(lockedUntil);
        if (!ready && !abandoned) {
            throw new IllegalStateException("Inbox is not available for claim");
        }
        status = PaymentInboxStatus.PROCESSING;
        lockedUntil = deadline;
        lockToken = UUID.randomUUID();
        attempts++;
        return lockToken;
    }

    public void markProcessed(UUID token, Instant now) {
        requireLease(token, now);
        status = PaymentInboxStatus.PROCESSED;
        processedAt = now;
        lastError = null;
        clearLease();
    }

    public void retry(UUID token, Instant now, Instant nextAvailableAt, String error) {
        requireLease(token, now);
        Objects.requireNonNull(nextAvailableAt, "nextAvailableAt must not be null");
        if (nextAvailableAt.isBefore(now)) {
            throw new IllegalArgumentException("Retry cannot be scheduled in the past");
        }
        String validatedError = text(error, "error", 2000);
        status = PaymentInboxStatus.PENDING;
        availableAt = nextAvailableAt;
        lastError = validatedError;
        clearLease();
    }

    public void requireReview(UUID token, Instant now, String reason) {
        finishWithError(token, now, reason, PaymentInboxStatus.REVIEW_REQUIRED);
    }

    public void fail(UUID token, Instant now, String reason) {
        finishWithError(token, now, reason, PaymentInboxStatus.FAILED);
    }

    private void finishWithError(UUID token, Instant now, String error, PaymentInboxStatus target) {
        requireLease(token, now);
        String validatedError = text(error, "error", 2000);
        status = target;
        lastError = validatedError;
        clearLease();
    }

    private void requireLease(UUID token, Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (status != PaymentInboxStatus.PROCESSING || token == null || !token.equals(lockToken)
                || !now.isBefore(lockedUntil)) {
            throw new IllegalStateException("Worker does not own a live inbox lease");
        }
    }

    public boolean ownsLiveLease(UUID token, Instant now) {
        return status == PaymentInboxStatus.PROCESSING && Objects.equals(lockToken, token)
                && token != null && lockedUntil != null && now.isBefore(lockedUntil);
    }

    private void clearLease() {
        lockedUntil = null;
        lockToken = null;
    }

    private static String text(String value, String name, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(name + " must contain 1-" + maxLength + " characters");
        }
        return value;
    }

    public record Notification(PaymentEnvironment environment, String merchantId, String type,
            String deduplicationKey, String payloadHash, String payload, String transactionId,
            String invoiceNumber, Instant occurredAt) {
        public Notification {
            Objects.requireNonNull(environment, "environment must not be null");
            text(merchantId, "merchantId", 100);
            text(type, "type", 100);
            text(deduplicationKey, "deduplicationKey", 255);
            if (payloadHash == null || !payloadHash.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("payloadHash must be a lowercase SHA-256 hash");
            }
            Objects.requireNonNull(payload, "payload must not be null");
            JsonNode tree = new ObjectMapper().readTree(payload);
            if (tree == null || !tree.isObject()) {
                throw new IllegalArgumentException("IPN payload must be a JSON object");
            }
            if (transactionId != null) {
                text(transactionId, "transactionId", 150);
            }
            if (invoiceNumber != null) {
                text(invoiceNumber, "invoiceNumber", 100);
            }
        }

        @Override
        public String toString() {
            return "Notification[type=" + type + ", payload=<redacted>]";
        }
    }
}
