package dev.booking.sports;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import dev.booking.sports.booking.domain.model.Booking;
import dev.booking.sports.booking.domain.model.BookingSlot;
import dev.booking.sports.booking.domain.repository.BookingRepository;
import dev.booking.sports.court.domain.model.Court;
import dev.booking.sports.court.domain.repository.CourtRepository;
import dev.booking.sports.court.domain.repository.SportRepository;
import dev.booking.sports.identity.domain.model.User;
import dev.booking.sports.identity.domain.repository.UserRepository;
import dev.booking.sports.payment.domain.enums.PaymentEnvironment;
import dev.booking.sports.payment.domain.model.Payment;
import dev.booking.sports.payment.domain.model.PaymentAttempt;
import dev.booking.sports.payment.domain.model.PaymentWebhookInbox;
import dev.booking.sports.payment.domain.repository.PaymentAttemptRepository;
import dev.booking.sports.payment.domain.repository.PaymentInboxRepository;
import dev.booking.sports.payment.domain.repository.PaymentRepository;
import dev.booking.sports.shared.outbox.OutboxRepository;
import dev.booking.sports.shared.upload.UploadAsset;
import dev.booking.sports.shared.upload.UploadAssetRepository;
import dev.booking.sports.support.BaseIntegrationTest;
import dev.booking.sports.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the actual locking SQL, including unversioned rows, on PostgreSQL. */
@IntegrationTest
@Transactional
class PersistenceLockQueriesIntegrationTest extends BaseIntegrationTest {
    @Autowired private UserRepository users;
    @Autowired private CourtRepository courts;
    @Autowired private SportRepository sports;
    @Autowired private BookingRepository bookings;
    @Autowired private PaymentRepository payments;
    @Autowired private PaymentAttemptRepository attempts;
    @Autowired private PaymentInboxRepository inboxes;
    @Autowired private UploadAssetRepository uploads;
    @Autowired private OutboxRepository outbox;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    private UUID ownerId, courtId, bookingId, paymentId, attemptId;
    private String invoice;
    private final Instant createdAt = Instant.parse("2000-01-01T00:00:00Z");

    @BeforeEach
    void fixtures() {
        User owner = users.saveAndFlush(User.register(UUID.randomUUID() + "@lock.test", "unused-hash", "Lock test", null));
        ownerId = owner.getId();
        Court court = new Court();
        court.setOwnerId(ownerId);
        court.setSport(sports.findAll().getFirst());
        court.setName("Lock test court");
        court.setAddress("Test address");
        court.setCity("Test city");
        courtId = courts.saveAndFlush(court).getId();
        Booking booking = Booking.hold("BK" + UUID.randomUUID().toString().substring(0, 20), ownerId,
                "VND", UUID.randomUUID().toString(), "a".repeat(64), createdAt.plusSeconds(600), createdAt,
                List.of(new BookingSlot.Spec(courtId, createdAt.plusSeconds(3600), createdAt.plusSeconds(7200), new BigDecimal("100"))));
        bookingId = bookings.saveAndFlush(booking).getId();
        Payment payment = payments.saveAndFlush(Payment.pending(bookingId, new BigDecimal("100"), "VND", createdAt.plusSeconds(600), createdAt));
        paymentId = payment.getId();
        invoice = "LOCK-" + UUID.randomUUID();
        var spec = new PaymentAttempt.CheckoutSpec(PaymentEnvironment.SANDBOX, "lock-test", invoice,
                "BANK_TRANSFER", List.of(new PaymentAttempt.CheckoutField("order_amount", "100")),
                UUID.randomUUID().toString(), "a".repeat(64));
        attemptId = attempts.saveAndFlush(PaymentAttempt.prepare(payment, 1, spec, createdAt)).getId();
        entityManager.clear();
    }

    @Test
    void courtLockCanLoadItsUnversionedSportInsideTheTransaction() {
        Court court = courts.findOwnedCourtForUpdate(courtId, ownerId).orElseThrow();
        assertThat(court.getSport().getCode()).isNotBlank();
        assertThat(court.getVersion()).isZero();
    }

    @Test
    void bookingOwnedLockRootLockAndNativeExpiryLockExecuteWithSlots() {
        assertThat(bookings.findOwnedByIdForUpdate(bookingId, ownerId).orElseThrow().getSlots()).hasSize(1);
        assertThat(bookings.findByIdForUpdate(bookingId)).isPresent();
        assertThat(bookings.lockExpiredHoldIds(100, Instant.now(), Instant.now().minusSeconds(120))).contains(bookingId);
    }

    @Test
    void paymentAndInvoiceLocksLoadWithoutLockingTheirHistory() {
        assertThat(payments.findByIdForUpdate(paymentId).orElseThrow().getStatusHistory()).hasSize(1);
        assertThat(payments.findByBookingIdForUpdate(bookingId).orElseThrow().getId()).isEqualTo(paymentId);
        assertThat(attempts.findInvoiceForUpdate(PaymentEnvironment.SANDBOX, "lock-test", invoice).orElseThrow().getId()).isEqualTo(attemptId);
    }

    @Test
    void unversionedUploadAssetSupportsPessimisticWrite() {
        UploadAsset asset = new UploadAsset();
        asset.setOwnerId(ownerId);
        asset.setResourceId(courtId);
        asset.setPurpose("COURT_IMAGE");
        asset.setFileName("lock.png");
        asset.setContentType("image/png");
        asset.setSizeBytes(10);
        asset.setStagingKey("staging/" + UUID.randomUUID());
        asset.setStorageKey("assets/" + UUID.randomUUID() + ".png");
        asset.setStatus(UploadAsset.Status.PENDING);
        asset.setExpiresAt(Instant.now().plusSeconds(900));
        UUID id = uploads.saveAndFlush(asset).getId();
        entityManager.clear();
        assertThat(uploads.lockById(id).orElseThrow().getOwnerId()).isEqualTo(ownerId);
    }

    @Test
    void unversionedInboxSupportsRootAndNativeClaimLocks() {
        var notification = new PaymentWebhookInbox.Notification(PaymentEnvironment.SANDBOX, "lock-test",
                "PAYMENT", UUID.randomUUID().toString(), "a".repeat(64), "{}", null, invoice, createdAt);
        UUID id = inboxes.saveAndFlush(PaymentWebhookInbox.receive(notification, createdAt)).getId();
        entityManager.clear();
        assertThat(inboxes.findForUpdate(id)).isPresent();
        assertThat(inboxes.findClaimable(Instant.now()).orElseThrow().getId()).isEqualTo(id);
    }

    @Test
    void unversionedOutboxSupportsNativeSkipLockedQuery() {
        UUID eventId = UUID.randomUUID();
        jdbc.update("""
                insert into outbox_events(event_id, aggregate_type, aggregate_id, event_type, event_version, payload, available_at)
                values (?, 'LOCK_TEST', ?, 'lock.test', 1, '{}'::jsonb, '2000-01-01T00:00:00Z')
                """, eventId, ownerId.toString());
        assertThat(outbox.lockPendingBatch(1)).extracting(event -> event.getEventId()).contains(eventId);
    }
}
