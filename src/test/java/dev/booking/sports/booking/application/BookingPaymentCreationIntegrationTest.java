package dev.booking.sports.booking.application;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import dev.booking.sports.booking.domain.repository.BookingRepository;
import dev.booking.sports.booking.publicapi.BookingPaymentFacade;
import dev.booking.sports.payment.api.error.PaymentErrorCode;
import dev.booking.sports.payment.application.PaymentCreationTransaction;
import dev.booking.sports.payment.config.SePayProperties;
import dev.booking.sports.payment.domain.enums.PaymentEnvironment;
import dev.booking.sports.payment.domain.repository.PaymentRepository;
import dev.booking.sports.payment.infrastructure.sepay.SePayCheckoutFactory;
import dev.booking.sports.shared.exception.ApiException;

/** Real PostgreSQL/Flyway/JPA transactions; no Redis, RabbitMQ or SePay network calls. */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "PAYMENT_TEST_JDBC_URL", matches = ".+")
@SpringJUnitConfig(BookingPaymentCreationIntegrationTest.Config.class)
class BookingPaymentCreationIntegrationTest {
    private Instant now;
    @Autowired private PaymentCreationTransaction transaction;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PaymentRepository payments;
    @Autowired private TestCheckoutFactory checkoutFactory;
    private UUID customer;
    private UUID booking;
    private UUID court;

    @BeforeEach
    void fixtures() {
        now = Instant.now();
        checkoutFactory.fail = false;
        customer = UUID.randomUUID();
        booking = UUID.randomUUID();
        court = UUID.randomUUID();
        jdbc.update("insert into users(id,email,password_hash,full_name) values (?,?,?,?)",
                customer, customer + "@payment.test", "test-only", "Payment test");
        jdbc.update("""
                insert into courts(id,owner_id,sport_id,name,address,city,status)
                values (?,?,'d1000000-0000-0000-0000-000000000001','Payment test','Address','City','ACTIVE')
                """, court, customer);
        jdbc.update("""
                insert into bookings(id,booking_code,customer_id,total_amount,status,hold_expires_at,
                    idempotency_key,idempotency_request_hash)
                values (?,?,?,300000,'HELD',?,'hold',?)
                """, booking, "BK" + booking.toString().substring(0, 20), customer,
                java.sql.Timestamp.from(now.plusSeconds(600)), "a".repeat(64));
        jdbc.update("""
                insert into booking_slots(id,booking_id,court_id,start_at,end_at,price_amount,status)
                values (?,?,?,?,?,300000,'HELD')
                """, UUID.randomUUID(), booking, court, java.sql.Timestamp.from(now.plusSeconds(3600)),
                java.sql.Timestamp.from(now.plusSeconds(7200)));
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from payment_status_history where payment_id in (select id from payments where booking_id=?)", booking);
        jdbc.update("delete from payment_attempts where payment_id in (select id from payments where booking_id=?)", booking);
        jdbc.update("delete from payments where booking_id=?", booking);
        jdbc.update("delete from booking_status_history where booking_id=?", booking);
        jdbc.update("delete from booking_slots where booking_id=?", booking);
        jdbc.update("delete from bookings where id=?", booking);
        jdbc.update("delete from courts where id=?", court);
        jdbc.update("delete from users where id=?", customer);
    }

    @Test
    void commitsBookingSlotPaymentAndOrderedPayloadAndReplaysOriginalAttempt() {
        var created = create("key");
        var replay = create("key");
        assertThat(replay).isEqualTo(created);
        assertThat(created.checkout().fields().getLast().name()).isEqualTo("signature");
        assertThat(jdbc.queryForObject("select status from bookings where id=?", String.class, booking)).isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.queryForObject("select status from booking_slots where booking_id=?", String.class, booking)).isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.queryForObject("select count(*) from payment_attempts where payment_id=?", Integer.class, created.paymentId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select checkout_payload->0->>'name' from payment_attempts where id=?",
                String.class, created.attemptId())).isEqualTo("order_amount");
        assertThat(jdbc.queryForObject("select count(*) from booking_status_history where booking_id=?", Integer.class, booking)).isEqualTo(1);
    }

    @Test
    void rollsBackAlreadyFlushedPaymentWhenCheckoutSigningFails() {
        checkoutFactory.fail = true;
        assertThatThrownBy(() -> create("key")).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(PaymentErrorCode.SEPAY_GATEWAY_NOT_CONFIGURED));
        assertThat(jdbc.queryForObject("select count(*) from payments where booking_id=?", Integer.class, booking)).isZero();
        assertThat(jdbc.queryForObject("select status from bookings where id=?", String.class, booking)).isEqualTo("HELD");
        assertThat(jdbc.queryForObject("select status from booking_slots where booking_id=?", String.class, booking)).isEqualTo("HELD");
    }

    @Test
    void databaseSerializesDifferentRequestKeysForSameBooking() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = CompletableFuture.supplyAsync(() -> race("first", ready, start), executor);
            var second = CompletableFuture.supplyAsync(() -> race("second", ready, start), executor);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("CREATED", "PAYMENT_ATTEMPT_IN_PROGRESS");
        } finally {
            start.countDown();
        }
        assertThat(jdbc.queryForObject("select count(*) from payments where booking_id=?", Integer.class, booking)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from payment_attempts a join payments p on p.id=a.payment_id where p.booking_id=?
                """, Integer.class, booking)).isEqualTo(1);
    }

    private String race(String key, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent test did not start");
            }
            create(key);
            return "CREATED";
        } catch (ApiException exception) {
            return exception.getErrorCode().code();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void nativePaymentListScopesRolesAndFiltersBeforeCounting() {
        create("list-key");
        var page = org.springframework.data.domain.PageRequest.of(0, 20);
        UUID outsider = UUID.randomUUID();
        assertThat(payments.findAccessiblePayments(customer, false, true, false, booking, null,
                "PENDING", null, null, page).getTotalElements()).isEqualTo(1);
        assertThat(payments.findAccessiblePayments(outsider, false, true, false, booking, null,
                null, null, null, page).getTotalElements()).isZero();
        assertThat(payments.findAccessiblePayments(customer, false, false, true, booking, null,
                null, null, null, page).getTotalElements()).isEqualTo(1);
        assertThat(payments.findAccessiblePayments(outsider, false, false, true, booking, null,
                null, null, null, page).getTotalElements()).isZero();
        assertThat(payments.findAccessiblePayments(outsider, true, false, false, booking, null,
                null, null, null, page).getTotalElements()).isEqualTo(1);
        assertThat(payments.findAccessiblePayments(customer, true, false, false, booking, outsider,
                null, null, null, page).getTotalElements()).isZero();
        assertThat(payments.findAccessiblePayments(customer, true, false, false, booking, null,
                null, null, Instant.parse("2000-01-01T00:00:00Z"), page).getTotalElements()).isZero();
    }

    private dev.booking.sports.payment.api.dto.response.PaymentCheckoutResponse create(String key) {
        return transaction.create(customer, booking, key, "a".repeat(64), "BANK_TRANSFER");
    }

    static class TestCheckoutFactory extends SePayCheckoutFactory {
        volatile boolean fail;

        TestCheckoutFactory(SePayProperties properties) {
            super(properties);
        }

        @Override
        public dev.booking.sports.payment.domain.model.PaymentAttempt.CheckoutSpec create(
                dev.booking.sports.payment.domain.model.Payment payment,
                dev.booking.sports.booking.publicapi.BookingPaymentSnapshot booking,
                UUID customerId, String method, String key, String hash) {
            if (fail) {
                throw new ApiException(PaymentErrorCode.SEPAY_GATEWAY_NOT_CONFIGURED);
            }
            return super.create(payment, booking, customerId, method, key, hash);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaAuditing
    @EnableJpaRepositories(basePackageClasses = {BookingRepository.class, PaymentRepository.class})
    @Import(PaymentCreationTransaction.class)
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource(System.getenv("PAYMENT_TEST_JDBC_URL"),
                    System.getenv().getOrDefault("PAYMENT_TEST_DB_USER", "postgres"),
                    System.getenv().getOrDefault("PAYMENT_TEST_DB_PASSWORD", "migration-check-only"));
        }
        @Bean Flyway flyway(DataSource dataSource) {
            Flyway flyway = Flyway.configure().dataSource(dataSource).load();
            flyway.migrate();
            return flyway;
        }
        @Bean @DependsOn("flyway")
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var bean = new LocalContainerEntityManagerFactoryBean();
            bean.setDataSource(dataSource);
            bean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            bean.setPackagesToScan("dev.booking.sports.booking.domain.model", "dev.booking.sports.payment.domain.model");
            bean.getJpaPropertyMap().put("hibernate.hbm2ddl.auto", "validate");
            return bean;
        }
        @Bean JpaTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }
        @Bean TestCheckoutFactory checkoutFactory(SePayProperties properties) {
            return new TestCheckoutFactory(properties);
        }
        @Bean BookingPaymentFacade bookingFacade(BookingRepository repository) {
            return new BookingPaymentFacadeAdapter(repository, org.mockito.Mockito.mock(dev.booking.sports.court.publicapi.CourtFacade.class), org.mockito.Mockito.mock(dev.booking.sports.shared.outbox.OutboxService.class));
        }
        @Bean SePayProperties sePayProperties() {
            return new SePayProperties(true, PaymentEnvironment.SANDBOX, "merchant", "test-secret",
                    "https://example.com/success", "https://example.com/error", "https://example.com/cancel");
        }
    }
}
