package dev.booking.sports.payment.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.hibernate.Session;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import dev.booking.sports.payment.domain.enums.*;
import dev.booking.sports.payment.domain.model.PaymentAttempt.CheckoutField;
import dev.booking.sports.shared.persistence.CreatableEntity;
import dev.booking.sports.shared.persistence.BaseEntity;

/** Opt-in real PostgreSQL mapping test; the supplied database must be disposable. */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "PAYMENT_TEST_JDBC_URL", matches = ".+")
class PaymentMappingIntegrationTest {
    @Test
    void validatesAllMappingsAndRoundTripsSettlementRefundHistoryAndOrderedJson() {
        String url = System.getenv("PAYMENT_TEST_JDBC_URL");
        String user = System.getenv().getOrDefault("PAYMENT_TEST_DB_USER", "postgres");
        String password = System.getenv().getOrDefault("PAYMENT_TEST_DB_PASSWORD", "migration-check-only");
        Flyway.configure().dataSource(url, user, password).load().migrate();
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.url", url)
                .applySetting("hibernate.connection.username", user)
                .applySetting("hibernate.connection.password", password)
                .applySetting("hibernate.hbm2ddl.auto", "validate")
                .build();
        try (var factory = new MetadataSources(registry)
                .addAnnotatedClass(Payment.class)
                .addAnnotatedClass(PaymentAttempt.class)
                .addAnnotatedClass(PaymentTransaction.class)
                .addAnnotatedClass(PaymentStatusHistory.class)
                .addAnnotatedClass(PaymentRefund.class)
                .addAnnotatedClass(PaymentRefundStatusHistory.class)
                .addAnnotatedClass(PaymentWebhookInbox.class)
                .buildMetadata().buildSessionFactory()) {
            UUID bookingId = UUID.randomUUID();
            UUID actorId = UUID.randomUUID();
            Instant now = Instant.now();
            UUID paymentId;
            UUID attemptId;
            UUID refundId;
            UUID inboxId;
            List<CheckoutField> fields = List.of(new CheckoutField("order_amount", "300000"),
                    new CheckoutField("merchant", "merchant"), new CheckoutField("signature", "signed-value"));
            try (Session session = factory.openSession()) {
                var tx = session.beginTransaction();
                session.doWork(connection -> {
                    try (var statement = connection.prepareStatement(
                            "insert into users (id,email,password_hash,full_name) values (?,?,?,?)")) {
                        statement.setObject(1, actorId);
                        statement.setString(2, actorId + "@payment.test");
                        statement.setString(3, "test-only");
                        statement.setString(4, "Payment test");
                        statement.executeUpdate();
                    }
                    try (var statement = connection.prepareStatement("""
                            insert into bookings (id,booking_code,customer_id,total_amount,status,
                                hold_expires_at,idempotency_key,idempotency_request_hash)
                            values (?,?,?,300000,'HELD',current_timestamp + interval '10 minutes',?,?)
                            """)) {
                        statement.setObject(1, bookingId);
                        statement.setString(2, "BK-" + UUID.randomUUID().toString().substring(0, 20));
                        statement.setObject(3, actorId);
                        statement.setString(4, UUID.randomUUID().toString());
                        statement.setString(5, "a".repeat(64));
                        statement.executeUpdate();
                    }
                });
                Payment payment = Payment.pending(bookingId, new BigDecimal("300000"), "VND", now.plusSeconds(600), now);
                stamp(payment, now);
                payment.getStatusHistory().forEach(history -> stamp(history, now));
                session.persist(payment);
                session.flush();
                PaymentAttempt attempt = PaymentAttempt.prepare(payment, 1,
                        new PaymentAttempt.CheckoutSpec(PaymentEnvironment.SANDBOX, "merchant",
                            "INV-" + bookingId, "BANK_TRANSFER", fields, "checkout", "a".repeat(64)), now);
                stamp(attempt, now);
                session.persist(attempt);
                session.flush();
                PaymentTransaction receipt = PaymentTransaction.record(new PaymentTransaction.Receipt(
                        PaymentEnvironment.SANDBOX, "merchant", "TX-" + bookingId, null, "ORDER-" + bookingId,
                        attempt.getInvoiceNumber(), "PAYMENT", "APPROVED", "BANK_TRANSFER",
                        new BigDecimal("300000.00"), "VND", now), now);
                receipt.match(attempt);
                stamp(receipt, now);
                session.persist(receipt);
                session.flush();
                payment.settle(receipt, now.plusSeconds(1));
                payment.getStatusHistory().forEach(history -> stamp(history, now));
                PaymentRefund refund = PaymentRefund.request(receipt, new BigDecimal("300000"),
                        "Cancelled booking", "refund", "b".repeat(64), actorId);
                refund.startProcessing(actorId, now.plusSeconds(2));
                refund.complete(actorId, "MANUAL-REF", null, "Bank transfer", now.plusSeconds(3));
                stamp(refund, now);
                refund.getStatusHistory().forEach(history -> stamp(history, now));
                session.persist(refund);
                PaymentWebhookInbox inbox = PaymentWebhookInbox.receive(new PaymentWebhookInbox.Notification(
                        PaymentEnvironment.SANDBOX, "merchant", "ORDER_PAID", "PAID:" + bookingId,
                        "c".repeat(64), "{\"notification_type\":\"ORDER_PAID\"}",
                        receipt.getProviderTransactionId(), attempt.getInvoiceNumber(), now), now);
                UUID token = inbox.claim(now, Duration.ofSeconds(30));
                inbox.markProcessed(token, now.plusSeconds(1));
                session.persist(inbox);
                tx.commit();
                paymentId = payment.getId();
                attemptId = attempt.getId();
                refundId = refund.getId();
                inboxId = inbox.getId();
            }
            try (Session session = factory.openSession()) {
                Payment payment = session.find(Payment.class, paymentId);
                assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
                assertThat(payment.getStatusHistory()).hasSize(2);
                assertThat(session.find(PaymentAttempt.class, attemptId).getCheckoutPayload()).isEqualTo(fields);
                PaymentRefund refund = session.find(PaymentRefund.class, refundId);
                assertThat(refund.getStatusHistory()).hasSize(3);
                assertThat(refund.getRefundReference()).isEqualTo("MANUAL-REF");
                assertThat(session.find(PaymentWebhookInbox.class, inboxId).getPayload()).contains("ORDER_PAID");
                assertThat(session.find(PaymentTransaction.class, payment.getSettledTransactionId())
                        .getResolutionStatus()).isEqualTo(PaymentResolutionStatus.APPLIED);
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    // Bare Hibernate has no Spring auditing handler; assign audit fields explicitly.
    private static void stamp(CreatableEntity entity, Instant now) {
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        if (entity instanceof BaseEntity mutable) {
            mutable.setUpdatedAt(now);
        }
    }
}
