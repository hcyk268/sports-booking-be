package dev.booking.sports.payment.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import dev.booking.sports.identity.infrastructure.security.AuthPrincipal;
import dev.booking.sports.payment.api.dto.request.*;
import dev.booking.sports.payment.domain.enums.*;
import dev.booking.sports.payment.domain.model.*;
import dev.booking.sports.payment.domain.repository.*;
import dev.booking.sports.payment.infrastructure.sepay.SePayOrderClient;
import dev.booking.sports.payment.infrastructure.redis.PaymentLockManager;
import dev.booking.sports.shared.exception.ApiException;
import dev.booking.sports.support.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"app.payment.inbox.enabled=false", "app.payment.sepay.merchant-id=merchant",
        "app.payment.sepay.secret-key=test-secret", "app.payment.sepay.environment=SANDBOX"})
class PaymentOperationsIntegrationTest extends BaseIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired PaymentRepository payments;
    @Autowired PaymentAttemptRepository attempts;
    @Autowired PaymentTransactionRepository receipts;
    @Autowired PaymentRefundService refunds;
    @Autowired PaymentReconciliationService reconciliation;
    @Autowired PaymentInboxWorker worker;
    @Autowired SePayIpnService ipn;
    @Autowired PaymentLockManager locks;
    @MockitoBean SePayOrderClient gateway;
    UUID actor, booking, court, payment, attempt, receipt;
    String invoice, gatewayOrder, transactionId;
    Instant now;

    @BeforeEach
    void seed() {
        now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        actor = UUID.randomUUID(); booking = UUID.randomUUID(); court = UUID.randomUUID();
        gatewayOrder = "ORDER-" + booking; transactionId = "TX-" + booking; invoice = "INV-" + booking;
        jdbc.update("insert into users(id,email,password_hash,full_name,status) values (?,?,?,?,'ACTIVE')",
                actor, actor + "@payment-ops.test", "test-only", "Payment operations");
        jdbc.update("""
                insert into courts(id,owner_id,sport_id,name,address,city,status)
                values (?,?,'d1000000-0000-0000-0000-000000000001','Payment test','Address','City','ACTIVE')
                """, court, actor);
        jdbc.update("""
                insert into bookings(id,booking_code,customer_id,total_amount,status,hold_expires_at,
                    idempotency_key,idempotency_request_hash)
                values (?,?,?,50000,'PENDING_PAYMENT',?,'hold',?)
                """, booking, "BK" + booking.toString().substring(0,20), actor,
                Timestamp.from(now.plusSeconds(600)), "a".repeat(64));
        jdbc.update("""
                insert into booking_slots(id,booking_id,court_id,start_at,end_at,price_amount,status)
                values (?,?,?,?,?,50000,'PENDING_PAYMENT')
                """, UUID.randomUUID(), booking, court, Timestamp.from(now.plusSeconds(3600)),
                Timestamp.from(now.plusSeconds(7200)));
        tx.executeWithoutResult(status -> {
            var p = payments.saveAndFlush(Payment.pending(booking, new BigDecimal("50000"), "VND", now.plusSeconds(600), now));
            payment = p.getId();
            var a = attempts.saveAndFlush(PaymentAttempt.prepare(p, 1, new PaymentAttempt.CheckoutSpec(
                    PaymentEnvironment.SANDBOX, "merchant", invoice, "BANK_TRANSFER",
                    List.of(new PaymentAttempt.CheckoutField("merchant", "merchant")), "checkout", "a".repeat(64)), now));
            attempt = a.getId();
        });
    }

    RequestPostProcessor as(String role) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthPrincipal(actor, actor + "@payment-ops.test", "test-token", Set.of(role), Set.of()),
                null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    UUID source() {
        if (receipt == null) receipt = tx.execute(status -> {
            var r = PaymentTransaction.record(new PaymentTransaction.Receipt(PaymentEnvironment.SANDBOX,
                    "merchant", transactionId, null, gatewayOrder, invoice, "PAYMENT", "APPROVED",
                    "BANK_TRANSFER", new BigDecimal("50000"), "VND", now), now);
            r.match(attempts.findById(attempt).orElseThrow());
            return receipts.saveAndFlush(r).getId();
        });
        return receipt;
    }

    JsonNode postAdmin(String path, Object body, String key, int expected) throws Exception {
        var request = post(path).with(as("ADMIN")).contentType("application/json").content(mapper.writeValueAsString(body));
        if (key != null) request.header("Idempotency-Key", key);
        var result = mvc.perform(request).andExpect(status().is(expected)).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    String base() { return "/api/v1/payments/refunds"; }
    CreateRefundRequest createRequest(int amount) {
        return new CreateRefundRequest(payment, source(), BigDecimal.valueOf(amount), "Booking cancelled");
    }

    @Test
    void allRefundEndpointsPersistVersionedHistoryAndCompletionReplay() throws Exception {
        var body = createRequest(20000);
        var created = postAdmin(base(), body, "create-key", 201);
        String id = created.path("id").asText();
        assertThat(postAdmin(base(), body, "create-key", 201).path("id").asText()).isEqualTo(id);
        mvc.perform(get(base() + "/" + id).with(as("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REQUESTED"));
        mvc.perform(get(base()).param("paymentId", payment.toString()).with(as("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1));
        var started = postAdmin(base() + "/" + id + "/processing", new RefundVersionRequest(0L), null, 200);
        assertThat(started.path("version").asLong()).isEqualTo(1);
        var completion = new CompleteRefundRequest(1L, "BANK-REF-001", null, "Manual bank transfer verified");
        var completed = postAdmin(base() + "/" + id + "/completion", completion, "complete-key", 200);
        assertThat(completed.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(completed.path("version").asLong()).isEqualTo(2);
        var replayed = postAdmin(base() + "/" + id + "/completion", completion, "complete-key", 200);
        for (String field : List.of("id","version","status","refundReference","amount","evidenceAssetId"))
            assertThat(replayed.path(field)).as(field).isEqualTo(completed.path(field));
        postAdmin(base() + "/" + id + "/completion", new CompleteRefundRequest(1L, "changed", null, null), "complete-key", 409);
        postAdmin(base(), new CreateRefundRequest(payment, source(), BigDecimal.ONE, "changed"), "create-key", 409);
        assertThat(jdbc.queryForObject("select count(*) from payment_refund_status_history where refund_id=?",
                Integer.class, UUID.fromString(id))).isEqualTo(3);
        var rejected = postAdmin(base(), createRequest(15000), "reject-key", 201);
        postAdmin(base() + "/" + rejected.path("id").asText() + "/rejection", new RefundDecisionRequest(0L, "Invalid request"), null, 200);
        var cancelled = postAdmin(base(), createRequest(30000), "cancel-key", 201);
        postAdmin(base() + "/" + cancelled.path("id").asText() + "/cancellation", new RefundDecisionRequest(0L, "Withdrawn"), null, 200);
        postAdmin(base(), createRequest(30001), "over-budget", 409);
        postAdmin(base(), createRequest(30000), "remaining-budget", 201);
    }

    @Test
    void deniedRolesValidationAndStateConflictsDoNotWrite() throws Exception {
        for (String role : List.of("CUSTOMER", "PROVIDER")) {
            mvc.perform(get(base()).with(as(role))).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/payments/" + payment + "/reconciliation").with(as(role)))
                    .andExpect(status().isForbidden());
            mvc.perform(post(base()).with(as(role)).header("Idempotency-Key","forbidden")
                    .contentType("application/json").content(mapper.writeValueAsString(createRequest(1))))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get(base())).andExpect(status().isUnauthorized());
        mvc.perform(get(base()).param("size","101").with(as("ADMIN"))).andExpect(status().isBadRequest());
        mvc.perform(get(base()).param("from","2026-12-02T00:00:00Z").param("to","2026-12-01T00:00:00Z")
                .with(as("ADMIN"))).andExpect(status().isBadRequest());
        mvc.perform(post(base()).with(as("ADMIN")).contentType("application/json")
                .content(mapper.writeValueAsString(createRequest(1)))).andExpect(status().isBadRequest());
        var created = postAdmin(base(), createRequest(10000), "create", 201);
        String path = base() + "/" + created.path("id").asText();
        postAdmin(path + "/completion", new CompleteRefundRequest(0L, "ref", null, null), "early", 409);
        postAdmin(path + "/processing", new RefundVersionRequest(1L), null, 409);
        postAdmin(path + "/processing", new RefundVersionRequest(0L), null, 200);
        postAdmin(path + "/rejection", new RefundDecisionRequest(1L, "too late"), null, 409);
        postAdmin(path + "/cancellation", new RefundDecisionRequest(1L, "too late"), null, 409);
        postAdmin(path + "/completion", new CompleteRefundRequest(1L, "ref", UUID.randomUUID(), null), "bad-evidence", 400);
        mvc.perform(get(path).with(as("ADMIN"))).andExpect(jsonPath("$.data.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.version").value(1));
    }

    @Test
    void concurrentRefundsCannotOverdrawTheSameReceipt() throws Exception {
        var request = createRequest(30000);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var results = new ArrayList<Future<String>>();
            for (int i = 0; i < 2; i++) {
                String key = "concurrent-" + i;
                results.add(pool.submit(() -> {
                    start.await();
                    try { refunds.create(actor, key, request); return "CREATED"; }
                    catch (ApiException exception) { return exception.getErrorCode().code(); }
                }));
            }
            start.countDown();
            assertThat(List.of(results.get(0).get(20, TimeUnit.SECONDS), results.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("CREATED", "REFUND_AMOUNT_EXCEEDED");
        }
    }

    @Test
    void concurrentDuplicateCreationIsOneRefund() throws Exception {
        var request = createRequest(50000);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var one = pool.submit(() -> { start.await(); return refunds.create(actor,"same",request).id(); });
            var two = pool.submit(() -> { start.await(); return refunds.create(actor,"same",request).id(); });
            start.countDown();
            assertThat(one.get(20,TimeUnit.SECONDS)).isEqualTo(two.get(20,TimeUnit.SECONDS));
        }
        assertThat(jdbc.queryForObject("select count(*) from payment_refunds where payment_id=?",Integer.class,payment)).isEqualTo(1);
    }


    @Test
    void completionClaimsOnlyReadyEvidenceForThisAdminAndRefund() throws Exception {
        var refund = refunds.create(actor, "evidence-refund", createRequest(10000));
        refunds.start(refund.id(), actor, refund.version());
        UUID asset = UUID.randomUUID();
        jdbc.update("""
                insert into shared_upload_assets(id,owner_id,purpose,resource_id,file_name,content_type,
                    size_bytes,staging_key,storage_key,status,expires_at)
                values (?,?,'COURT_IMAGE',?,'proof.png','image/png',100,?,?,'READY',now()+interval '1 hour')
                """,asset,actor,refund.id(),"staging/"+asset,"final/"+asset);
        String path = base()+"/"+refund.id()+"/completion";
        var request = new CompleteRefundRequest(1L,"PROOF-REF",asset,"");
        postAdmin(path,request,"proof",400);
        jdbc.update("update shared_upload_assets set purpose='REFUND_EVIDENCE' where id=?",asset);
        postAdmin(path,request,"proof",200);
        postAdmin(path,request,"proof",200);
        assertThat(jdbc.queryForObject("select status from shared_upload_assets where id=?",String.class,asset)).isEqualTo("ATTACHED");
    }

    @Test
    void concurrentCompletionCannotRecordTwoTransfers() throws Exception {
        var refund = refunds.create(actor,"concurrent-complete",createRequest(50000));
        var processing = refunds.start(refund.id(),actor,refund.version());
        try(var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var results = new ArrayList<Future<String>>();
            for(int i=0;i<2;i++) {
                String key = "complete-"+i;
                results.add(pool.submit(() -> {
                    start.await();
                    try {
                        refunds.complete(refund.id(),actor,key,
                                new CompleteRefundRequest(processing.version(),key,null,null));
                        return "COMPLETED";
                    } catch(ApiException ex) { return ex.getErrorCode().code(); }
                }));
            }
            start.countDown();
            assertThat(List.of(results.get(0).get(20,TimeUnit.SECONDS),results.get(1).get(20,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("COMPLETED","OPTIMISTIC_LOCK_CONFLICT");
        }
    }

    @Test
    void wrongTransactionAndVoidedCollectionCannotBeRefunded() throws Exception {
        var body = createRequest(1);
        postAdmin(base(),new CreateRefundRequest(payment,UUID.randomUUID(),BigDecimal.ONE,"wrong"),"wrong",404);
        jdbc.update("update payment_transactions set provider_status='VOID' where id=?",source());
        postAdmin(base(),body,"voided",409);
        assertThat(jdbc.queryForObject("select count(*) from payment_refunds where payment_id=?",Integer.class,payment)).isZero();
    }

    @Test
    void pendingAndCancelledGatewayStatesDoNotConfirmBooking() {
        gatewayReturns(snapshot("AUTHENTICATION_NOT_NEEDED",null));
        reconciliation.reconcile(payment);
        assertThat(attempts.findById(attempt).orElseThrow().getStatus()).isEqualTo(PaymentAttemptStatus.PENDING);
        gatewayReturns(snapshot("CANCELLED",null));
        reconciliation.reconcile(payment);
        assertThat(attempts.findById(attempt).orElseThrow().getStatus()).isEqualTo(PaymentAttemptStatus.CANCELLED);
        assertThat(payments.findById(payment).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void mismatchedOrderNeverEntersSettlementQueue() {
        var valid = snapshot("CAPTURED","APPROVED");
        var order = valid.order();
        gatewayReturns(new SePayOrderClient.Snapshot(new SePayIpnRequest.Order(order.id(),order.orderId(),order.orderStatus(),
                order.orderCurrency(),order.orderAmount(),"WRONG-INVOICE",null,null,null,null),valid.transactions()));
        assertThatThrownBy(() -> reconciliation.reconcile(payment)).isInstanceOf(ApiException.class);
        worker.runBatch();
        assertThat(payments.findById(payment).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(jdbc.queryForObject("select count(*) from payment_webhook_inbox where invoice_number=?",Integer.class,invoice)).isZero();
    }

    SePayOrderClient.Snapshot snapshot(String state, String txStatus) {
        var order = new SePayIpnRequest.Order("ORDER-UUID-" + booking, gatewayOrder, state, "VND",
                new BigDecimal("50000"), invoice, null, null, null, null);
        var transactions = txStatus == null ? List.<SePayIpnRequest.Transaction>of() : List.of(
                new SePayIpnRequest.Transaction(transactionId, null, "BANK_TRANSFER", "PAYMENT", txStatus,
                        new BigDecimal("50000"), "VND", java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
                                .withZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).format(now), null, null, null, null, null, null));
        return new SePayOrderClient.Snapshot(order, transactions);
    }

    void gatewayReturns(SePayOrderClient.Snapshot response) {
        when(gateway.find(PaymentEnvironment.SANDBOX,"merchant",invoice)).thenReturn(Optional.of(response));
    }

    @Test
    void reconciliationSettlesThroughInboxAndDuplicateIpnDoesNotSettleTwice() throws Exception {
        var response = snapshot("CAPTURED","APPROVED");
        gatewayReturns(response);
        postAdmin("/api/v1/payments/" + payment + "/reconciliation", Map.of(),null,202);
        assertThat(payments.findById(payment).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PENDING);
        worker.runBatch();
        assertThat(payments.findById(payment).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(jdbc.queryForObject("select status from bookings where id=?",String.class,booking)).isEqualTo("CONFIRMED");
        var transaction = response.transactions().getFirst();
        var ipnTx = new SePayIpnRequest.Transaction(transaction.id(), "bank-ref-from-ipn", transaction.paymentMethod(),
                transaction.transactionType(), transaction.transactionStatus(), transaction.transactionAmount(),
                transaction.transactionCurrency(), transaction.transactionDate(), null,null,null,null,null,null);
        ipn.receive("test-secret",mapper.writeValueAsString(new SePayIpnRequest(now.getEpochSecond(),"ORDER_PAID",
                response.order(),ipnTx,null)));
        reconciliation.reconcile(payment);
        worker.runBatch();
        assertThat(jdbc.queryForObject("select count(*) from payment_transactions where payment_id=?",Integer.class,payment)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from payment_status_history where payment_id=? and to_status='SUCCEEDED'",
                Integer.class,payment)).isEqualTo(1);
    }

    @Test
    void capturedWithoutTransactionsAndMissingOrderNeverInventSuccess() {
        gatewayReturns(snapshot("CAPTURED",null));
        reconciliation.reconcile(payment);
        assertThat(attempts.findById(attempt).orElseThrow().getStatus()).isEqualTo(PaymentAttemptStatus.UNKNOWN);
        when(gateway.find(PaymentEnvironment.SANDBOX,"merchant",invoice)).thenReturn(Optional.empty());
        reconciliation.reconcile(payment);
        assertThat(payments.findById(payment).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(attempts.findById(attempt).orElseThrow().getLastError()).isEqualTo("GATEWAY_ORDER_NOT_FOUND");
    }

    @Test
    void gatewayFailureIsExplicitAndConcurrentReconciliationIsRejected() throws Exception {
        when(gateway.find(PaymentEnvironment.SANDBOX,"merchant",invoice)).thenThrow(
                new ApiException(dev.booking.sports.payment.api.error.PaymentErrorCode.SEPAY_GATEWAY_UNAVAILABLE));
        postAdmin("/api/v1/payments/" + payment + "/reconciliation",Map.of(),null,503);
        assertThat(attempts.findById(attempt).orElseThrow().getReconciliationAttempts()).isEqualTo(1);
        assertThat(attempts.findById(attempt).orElseThrow().getStatus()).isEqualTo(PaymentAttemptStatus.PREPARED);
        try (var held = locks.acquireReconciliation(payment)) {
            postAdmin("/api/v1/payments/" + payment + "/reconciliation",Map.of(),null,409);
        }
    }

    @Test
    void cancelledBookingWithMoneyIsPreservedForReviewAndCanBeRefunded() {
        jdbc.update("update bookings set status='CANCELLED',cancelled_by=?,cancelled_at=now(),cancellation_reason='test' where id=?",actor,booking);
        jdbc.update("update booking_slots set status='CANCELLED' where booking_id=?",booking);
        jdbc.update("update payments set status='CANCELLED' where id=?",payment);
        gatewayReturns(snapshot("CAPTURED","APPROVED"));
        reconciliation.reconcile(payment);
        worker.runBatch();
        var source = receipts.findByProviderAndEnvironmentAndMerchantIdAndProviderTransactionId(
                PaymentProvider.SEPAY,PaymentEnvironment.SANDBOX,"merchant",transactionId).orElseThrow();
        assertThat(source.getResolutionStatus()).isEqualTo(PaymentResolutionStatus.REVIEW_REQUIRED);
        assertThat(payments.findById(payment).orElseThrow().getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        var refund = refunds.create(actor,"late-money",new CreateRefundRequest(payment,source.getId(),
                BigDecimal.valueOf(50000),"Cancelled booking collected money"));
        assertThat(refund.status()).isEqualTo(PaymentRefundStatus.REQUESTED);
    }
}
