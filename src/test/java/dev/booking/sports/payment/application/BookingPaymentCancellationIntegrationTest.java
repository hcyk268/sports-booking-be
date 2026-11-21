package dev.booking.sports.payment.application;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import dev.booking.sports.booking.domain.event.BookingCancelledEvent;
import dev.booking.sports.payment.infrastructure.messaging.BookingCancellationMessage;
import dev.booking.sports.payment.infrastructure.messaging.BookingCancellationRabbitConfig;
import dev.booking.sports.support.BaseIntegrationTest;
import dev.booking.sports.support.IntegrationTest;
import tools.jackson.databind.ObjectMapper;

@IntegrationTest
class BookingPaymentCancellationIntegrationTest extends BaseIntegrationTest {
    @Autowired BookingPaymentCancellationService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired RabbitTemplate rabbit;
    @Autowired RabbitListenerEndpointRegistry listeners;
    @Autowired ObjectMapper mapper;
    private UUID customer;
    private UUID booking;
    private UUID payment;
    private BookingCancelledEvent event;

    @BeforeEach
    void fixtures() {
        listeners.stop();
        purgeQueues();
        customer = UUID.randomUUID();
        booking = UUID.randomUUID();
        payment = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("insert into users(id,email,password_hash,full_name,status) values (?,?,? ,?,'ACTIVE')",
                customer, customer + "@cancel.test", "test-only", "Cancellation test");
        jdbc.update("""
                insert into bookings(id,booking_code,customer_id,total_amount,status,hold_expires_at,
                    idempotency_key,idempotency_request_hash,cancelled_by,cancelled_at,cancellation_reason)
                values (?,?,?,50000,'CANCELLED',?,'hold',?,?,?,'Change of plans')
                """, booking, "BK" + booking.toString().substring(0, 20), customer,
                Timestamp.from(now.plusSeconds(600)), "a".repeat(64), customer, Timestamp.from(now));
        jdbc.update("""
                insert into payments(id,booking_id,amount,status,expires_at)
                values (?,?,50000,'PENDING',?)
                """, payment, booking, Timestamp.from(now.plusSeconds(600)));
        event = new BookingCancelledEvent(booking, "BK-test", customer, new BigDecimal("50000"), "VND",
                customer, "Change of plans", now, List.of());
    }

    @AfterEach
    void cleanup() {
        listeners.getListenerContainer("bookingPaymentCancellationConsumer").stop();
        purgeQueues();
        jdbc.update("delete from payment_status_history where payment_id=?", payment);
        jdbc.update("delete from payments where id=?", payment);
        jdbc.update("delete from bookings where id=?", booking);
        jdbc.update("delete from users where id=?", customer);
    }

    private void purgeQueues() {
        var admin = new RabbitAdmin(rabbit);
        admin.purgeQueue(BookingCancellationRabbitConfig.QUEUE);
        admin.purgeQueue(BookingCancellationRabbitConfig.DLQ);
        for (int attempt = 1; attempt <= BookingCancellationRabbitConfig.MAX_RETRIES; attempt++) {
            admin.purgeQueue(BookingCancellationRabbitConfig.retryQueue(attempt));
        }
    }

    private String paymentStatus() {
        return jdbc.queryForObject("select status from payments where id=?", String.class, payment);
    }

    private int cancellationHistoryCount() {
        return jdbc.queryForObject("select count(*) from payment_status_history where payment_id=? and to_status='CANCELLED'",
                Integer.class, payment);
    }

    @Test
    void concurrentDeliveriesCommitOneTransitionAndOneHistory() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var first = executor.submit(() -> { start.await(); service.cancelPendingPayment(event); return null; });
            var second = executor.submit(() -> { start.await(); service.cancelPendingPayment(event); return null; });
            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        }
        assertThat(paymentStatus()).isEqualTo("CANCELLED");
        assertThat(cancellationHistoryCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select changed_by from payment_status_history where payment_id=?",
                UUID.class, payment)).isEqualTo(customer);
        assertThat(jdbc.queryForObject("select reason from payment_status_history where payment_id=?",
                String.class, payment)).isEqualTo("Change of plans");
    }

    @Test
    void noPaymentAndInvalidBookingStateDoNotCreateOrCancelPayments() {
        jdbc.update("delete from payments where id=?", payment);
        service.cancelPendingPayment(event);
        assertThat(jdbc.queryForObject("select count(*) from payments where booking_id=?", Integer.class, booking)).isZero();
        jdbc.update("update bookings set status='HELD' where id=?", booking);
        assertThatThrownBy(() -> service.cancelPendingPayment(event)).isInstanceOf(IllegalArgumentException.class);
        assertThat(cancellationHistoryCount()).isZero();
    }

    @Test
    void rabbitEventCommitsCancellationAndDuplicateIsNoOp() {
        var envelope = new BookingCancellationMessage(UUID.randomUUID(), "BOOKING", booking.toString(),
                "booking.cancelled", 1, event);
        var raw = new Message(mapper.writeValueAsBytes(envelope));
        // Real outbox messages identify the producer envelope; the consumer owns decoding.
        raw.getMessageProperties().setHeader("__TypeId__",
                "dev.booking.sports.booking.infrastructure.messaging.BookingEventMessage");
        var listener = listeners.getListenerContainer("bookingPaymentCancellationConsumer");
        listener.start();
        // Target this consumer only; the shared booking exchange also fans out to notifications.
        rabbit.send("", BookingCancellationRabbitConfig.QUEUE, raw);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(paymentStatus()).isEqualTo("CANCELLED");
            assertThat(cancellationHistoryCount()).isEqualTo(1);
        });
        service.cancelPendingPayment(event);
        assertThat(cancellationHistoryCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from notifications where recipient_id=?",
                Integer.class, customer)).isZero();
    }
}
