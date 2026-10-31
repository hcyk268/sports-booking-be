package dev.booking.sports.booking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import dev.booking.sports.booking.api.dto.request.CreateBookingHoldRequest;
import dev.booking.sports.booking.api.dto.request.CancelBookingRequest;
import dev.booking.sports.booking.api.dto.response.BookingResponse;
import dev.booking.sports.booking.api.error.BookingErrorCode;
import dev.booking.sports.booking.domain.enums.BookingSlotStatus;
import dev.booking.sports.booking.domain.enums.BookingStatus;
import dev.booking.sports.booking.domain.repository.BookingRepository;
import dev.booking.sports.shared.exception.ApiException;
import dev.booking.sports.shared.exception.ConcurrencyErrorCode;

@SpringBootTest
@ActiveProfiles("test")
class BookingCreateDatabaseTest {

    @Autowired
    private BookingCreateService createService;

    @Autowired
    private BookingCancellationService cancellationService;

    @Autowired
    private BookingExpiryService expiryService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private BookingRepository bookingRepository;

    private UUID ownerId;
    private UUID firstCustomerId;
    private UUID secondCustomerId;
    private UUID courtId;
    private LocalDate date;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        firstCustomerId = UUID.randomUUID();
        secondCustomerId = UUID.randomUUID();
        courtId = UUID.randomUUID();
        date = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(30);

        insertUser(ownerId);
        insertUser(firstCustomerId);
        insertUser(secondCustomerId);
        jdbc.update("""
                insert into courts (id, owner_id, sport_id, name, address, city, status)
                values (?, ?, 'd1000000-0000-0000-0000-000000000001',
                        'Booking test court', 'Test address', 'Test city', 'ACTIVE')
                """, courtId, ownerId);
        jdbc.update("""
                insert into court_operating_hours
                    (id, court_id, day_of_week, opening_time, closing_time,
                     slot_duration_minutes)
                values (?, ?, ?, '08:00', '20:00', 120)
                """, UUID.randomUUID(), courtId, date.getDayOfWeek().name());
        jdbc.update("""
                insert into court_price_rules
                    (id, court_id, day_of_week, start_time, end_time, price_per_hour)
                values (?, ?, ?, '08:00', '20:00', 100000)
                """, UUID.randomUUID(), courtId, date.getDayOfWeek().name());
    }

    @AfterEach
    void tearDown() {
        jdbc.update("""
                delete from outbox_events where aggregate_id in (
                    select distinct b.id::text from bookings b
                    join booking_slots s on s.booking_id = b.id
                    where s.court_id = ?)
                """, courtId);
        jdbc.update("""
                delete from booking_status_history where booking_id in (
                    select distinct b.id from bookings b
                    join booking_slots s on s.booking_id = b.id
                    where s.court_id = ?)
                """, courtId);
        jdbc.update("delete from booking_slots where court_id = ?", courtId);
        jdbc.update("delete from bookings where customer_id in (?, ?)",
                firstCustomerId, secondCustomerId);
        jdbc.update("delete from court_price_rules where court_id = ?", courtId);
        jdbc.update("delete from court_operating_hours where court_id = ?", courtId);
        jdbc.update("delete from courts where id = ?", courtId);
        jdbc.update("delete from users where id in (?, ?, ?)",
                ownerId, firstCustomerId, secondCustomerId);
    }

    @Test
    void listsBookingsWithAndWithoutDateFiltersAndCountsAllPages() {
        BookingResponse first = createService.createBooking(
                firstCustomerId, "query-first", request(slot(8, 10)));
        BookingResponse second = createService.createBooking(
                firstCustomerId, "query-second", request(slot(10, 12)));
        var pageable = PageRequest.of(0, 1);
        var start = date.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        var end = date.plusDays(1).atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();

        // A full first page forces execution of the count query as well.
        for (boolean filterByDate : List.of(false, true)) {
            var rangeStart = filterByDate ? start : null;
            var rangeEnd = filterByDate ? end : null;
            var customerPage = bookingRepository.findCustomerBookings(
                    firstCustomerId, null, rangeStart, rangeEnd, pageable);
            var adminPage = bookingRepository.findBookings(
                    firstCustomerId, null, null, rangeStart, rangeEnd, pageable);
            var providerPage = bookingRepository.findProviderBookings(
                    Set.of(courtId), null, null, null, rangeStart, rangeEnd, pageable);
            for (var result : List.of(customerPage, adminPage, providerPage)) {
                assertThat(result.getTotalElements()).isEqualTo(2);
                assertThat(result.getContent()).hasSize(1);
                assertThat(result.getContent().getFirst().getId()).isIn(first.id(), second.id());
            }
        }

        assertThat(bookingRepository.findCustomerBookings(
                firstCustomerId, null, end, end.plusSeconds(86400), pageable)).isEmpty();
        assertThat(bookingRepository.findCustomerBookings(
                firstCustomerId, BookingStatus.CONFIRMED, null, null, pageable)).isEmpty();
        assertThat(bookingRepository.findProviderBookings(
                Set.of(UUID.randomUUID()), firstCustomerId, null, null, null, null, pageable)).isEmpty();
    }

    @Test
    void replaysSameSelectionInAnyOrderAndRejectsDoubleBooking() {
        var forward = request(slot(8, 10), slot(10, 12));
        var reversed = request(slot(10, 12), slot(8, 10));
        BookingResponse created = createService.createBooking(
                firstCustomerId, "same-request", forward);
        BookingResponse replay = createService.createBooking(
                firstCustomerId, "same-request", reversed);

        assertThat(replay.id()).isEqualTo(created.id());
        assertThat(created.slots()).hasSize(2);
        assertThat(created.totalAmount()).isEqualByComparingTo("400000");
        assertThat(jdbc.queryForObject(
                "select count(*) from bookings where customer_id = ?",
                Integer.class, firstCustomerId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from outbox_events where aggregate_id = ?",
                Integer.class, created.id().toString())).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("""
                insert into booking_slots
                    (id, booking_id, court_id, start_at, end_at,
                     price_amount, currency, status)
                select ?, booking_id, court_id,
                       start_at + interval '1 hour', end_at + interval '1 hour',
                       price_amount, currency, 'HELD'
                from booking_slots where id = ?
                """, UUID.randomUUID(), created.slots().getFirst().id()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> createService.createBooking(
                firstCustomerId, "same-request", request(slot(18, 20))))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.getErrorCode())
                                .isEqualTo(BookingErrorCode.IDEMPOTENCY_KEY_REUSED));
        assertThatThrownBy(() -> createService.createBooking(
                secondCustomerId, "other-request", request(slot(8, 10))))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.getErrorCode())
                                .isEqualTo(BookingErrorCode.BOOKING_SLOT_UNAVAILABLE));
    }

    @Test
    void concurrentOppositeOrdersProduceOneBooking() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = CompletableFuture.supplyAsync(() -> createAfterStart(
                    start, firstCustomerId, "first", request(slot(8, 10), slot(10, 12))),
                    executor);
            var second = CompletableFuture.supplyAsync(() -> createAfterStart(
                    start, secondCustomerId, "second", request(slot(10, 12), slot(8, 10))),
                    executor);
            start.countDown();

            var results = List.of(first.get(15, TimeUnit.SECONDS),
                    second.get(15, TimeUnit.SECONDS));
            assertThat(results).filteredOn(result -> result instanceof UUID).hasSize(1);
            assertThat(results).filteredOn(result ->
                    result == BookingErrorCode.BOOKING_SLOT_UNAVAILABLE).hasSize(1);
            assertThat(jdbc.queryForObject(
                    "select count(*) from booking_slots where court_id = ?",
                    Integer.class, courtId)).isEqualTo(2);
        }
    }

    @Test
    void expiredHoldReleasesSlotForNextBooking() {
        BookingResponse first = createService.createBooking(
                firstCustomerId, "expiring", request(slot(8, 10)));
        jdbc.update("update bookings set hold_expires_at = now() - interval '1 second' "
                        + "where id = ?", first.id());

        assertThat(expiryService.expireBatch(25)).isEqualTo(1);
        BookingResponse second = createService.createBooking(
                secondCustomerId, "after-expiry", request(slot(8, 10)));

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(jdbc.queryForObject(
                "select status from booking_slots where booking_id = ?",
                String.class, first.id())).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject(
                "select count(*) from outbox_events where aggregate_id = ?",
                Integer.class, first.id().toString())).isEqualTo(2);
    }

    @Test
    void pendingPaymentHoldWaitsForSettlementGraceBeforeReleasingSlots() {
        BookingResponse held = createService.createBooking(
                firstCustomerId, "pending-payment-grace", request(slot(8, 10)));
        jdbc.update("update bookings set status='PENDING_PAYMENT', hold_expires_at=now()-interval '30 seconds' where id=?",
                held.id());
        jdbc.update("update booking_slots set status='PENDING_PAYMENT' where booking_id=?", held.id());

        assertThat(expiryService.expireBatch(25)).isZero();
        assertThat(jdbc.queryForObject("select status from bookings where id=?", String.class, held.id()))
                .isEqualTo("PENDING_PAYMENT");

        jdbc.update("update bookings set hold_expires_at=now()-interval '3 minutes' where id=?", held.id());
        assertThat(expiryService.expireBatch(25)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from booking_slots where booking_id=?", String.class, held.id()))
                .isEqualTo("EXPIRED");
    }

    @Test
    void customerCancellationReleasesSlotsAndEnqueuesEvent() {
        BookingResponse held = createService.createBooking(
                firstCustomerId, "cancel-by-customer", request(slot(8, 10), slot(10, 12)));

        BookingResponse cancelled = cancellationService.cancelBooking(
                viewer(firstCustomerId, BookingActorRole.CUSTOMER),
                held.id(),
                new CancelBookingRequest(held.version(), "Change of plans"));

        assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(cancelled.cancelledBy()).isEqualTo(firstCustomerId);
        assertThat(cancelled.cancellationReason()).isEqualTo("Change of plans");
        assertThat(cancelled.version()).isEqualTo(held.version() + 1);
        assertThat(cancelled.slots()).allSatisfy(slot ->
                assertThat(slot.status()).isEqualTo(BookingSlotStatus.CANCELLED));
        assertThat(jdbc.queryForObject("""
                select payload ->> 'cancellationReason' from outbox_events
                where aggregate_id = ? and event_type = 'booking.cancelled'
                """, String.class, held.id().toString())).isEqualTo("Change of plans");
        assertThat(jdbc.queryForObject("""
                select count(*) from booking_status_history
                where booking_id = ? and from_status = 'HELD'
                    and to_status = 'CANCELLED' and changed_by = ?
                """, Integer.class, held.id(), firstCustomerId)).isEqualTo(1);

        BookingResponse next = createService.createBooking(
                secondCustomerId, "after-cancellation", request(slot(8, 10), slot(10, 12)));
        assertThat(next.id()).isNotEqualTo(held.id());
    }

    @Test
    void cancellationChecksAccessVersionAndTerminalState() {
        BookingResponse held = createService.createBooking(
                firstCustomerId, "cancel-by-provider", request(slot(8, 10)));
        CancelBookingRequest current = new CancelBookingRequest(held.version(), "Court unavailable");

        assertThatThrownBy(() -> cancellationService.cancelBooking(
                viewer(secondCustomerId, BookingActorRole.CUSTOMER), held.id(), current))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.getErrorCode())
                                .isEqualTo(BookingErrorCode.BOOKING_NOT_FOUND));
        assertThatThrownBy(() -> cancellationService.cancelBooking(
                viewer(ownerId, BookingActorRole.PROVIDER), held.id(),
                new CancelBookingRequest(held.version() + 1, "Court unavailable")))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.getErrorCode())
                                .isEqualTo(ConcurrencyErrorCode.OPTIMISTIC_LOCK_CONFLICT));

        BookingResponse cancelled = cancellationService.cancelBooking(
                viewer(ownerId, BookingActorRole.PROVIDER), held.id(), current);
        assertThat(cancelled.cancelledBy()).isEqualTo(ownerId);
        assertThatThrownBy(() -> cancellationService.cancelBooking(
                viewer(ownerId, BookingActorRole.PROVIDER), held.id(),
                new CancelBookingRequest(cancelled.version(), "Again")))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.getErrorCode())
                                .isEqualTo(BookingErrorCode.BOOKING_CANCELLATION_NOT_ALLOWED));
        assertThat(jdbc.queryForObject("""
                select count(*) from outbox_events
                where aggregate_id = ? and event_type = 'booking.cancelled'
                """, Integer.class, held.id().toString())).isEqualTo(1);

        BookingResponse second = createService.createBooking(
                secondCustomerId, "cancel-by-admin", request(slot(8, 10)));
        BookingResponse adminCancelled = cancellationService.cancelBooking(
                viewer(firstCustomerId, BookingActorRole.ADMIN), second.id(),
                new CancelBookingRequest(second.version(), "Administrative cancellation"));
        assertThat(adminCancelled.cancelledBy()).isEqualTo(firstCustomerId);
    }

    @Test
    void concurrentCancellationsRecordOneTransition() throws Exception {
        BookingResponse held = createService.createBooking(
                firstCustomerId, "concurrent-cancellation", request(slot(8, 10)));
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = CompletableFuture.supplyAsync(() -> cancelAfterStart(
                    start, held.id(), held.version()), executor);
            var second = CompletableFuture.supplyAsync(() -> cancelAfterStart(
                    start, held.id(), held.version()), executor);
            start.countDown();

            var results = List.of(first.get(15, TimeUnit.SECONDS),
                    second.get(15, TimeUnit.SECONDS));
            assertThat(results).filteredOn(result -> result instanceof BookingResponse)
                    .hasSize(1);
            assertThat(results).filteredOn(result ->
                    result == ConcurrencyErrorCode.OPTIMISTIC_LOCK_CONFLICT)
                    .hasSize(1);
            assertThat(jdbc.queryForObject("""
                    select count(*) from outbox_events
                    where aggregate_id = ? and event_type = 'booking.cancelled'
                    """, Integer.class, held.id().toString())).isEqualTo(1);
        }
    }

    private Object createAfterStart(
            CountDownLatch start,
            UUID customerId,
            String key,
            CreateBookingHoldRequest request) {
        try {
            start.await();
            return createService.createBooking(customerId, key, request).id();
        } catch (ApiException error) {
            return error.getErrorCode();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }

    private Object cancelAfterStart(
            CountDownLatch start,
            UUID bookingId,
            long version) {
        try {
            start.await();
            return cancellationService.cancelBooking(
                    viewer(firstCustomerId, BookingActorRole.CUSTOMER),
                    bookingId,
                    new CancelBookingRequest(version, "Concurrent request"));
        } catch (ApiException error) {
            return error.getErrorCode();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }

    private void insertUser(UUID id) {
        jdbc.update("""
                insert into users (id, email, password_hash, full_name)
                values (?, ?, 'unused', 'Booking Test')
                """, id, id + "@booking-test.local");
    }

    private CreateBookingHoldRequest request(CreateBookingHoldRequest.Slot... slots) {
        return new CreateBookingHoldRequest(courtId, List.of(slots));
    }

    private CreateBookingHoldRequest.Slot slot(int startHour, int endHour) {
        return new CreateBookingHoldRequest.Slot(
                date, LocalTime.of(startHour, 0), LocalTime.of(endHour, 0));
    }

    private BookingViewer viewer(UUID userId, BookingActorRole role) {
        return new BookingViewer(userId, Set.of(role));
    }
}
