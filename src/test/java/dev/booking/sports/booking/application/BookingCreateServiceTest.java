package dev.booking.sports.booking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.booking.sports.booking.api.dto.request.CreateBookingHoldRequest;
import dev.booking.sports.booking.api.dto.response.BookingResponse;
import dev.booking.sports.booking.api.error.BookingErrorCode;
import dev.booking.sports.booking.config.BookingHoldProperties;
import dev.booking.sports.booking.config.BookingProperties;
import dev.booking.sports.booking.domain.model.Booking;
import dev.booking.sports.booking.domain.repository.BookingRepository;
import dev.booking.sports.booking.infrastructure.redis.BookingLockManager;
import dev.booking.sports.court.publicapi.CourtFacade;
import dev.booking.sports.court.publicapi.CourtSlotQuote;
import dev.booking.sports.court.publicapi.CourtSlotSelection;
import dev.booking.sports.shared.exception.ApiException;

@ExtendWith(MockitoExtension.class)
class BookingCreateServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private CourtFacade courtFacade;
    @Mock private BookingLockManager lockManager;
    @Mock private BookingCreationTransaction transaction;

    private BookingCreateService service;

    @BeforeEach
    void setUp() {
        service = new BookingCreateService(
                bookingRepository,
                courtFacade,
                lockManager,
                transaction,
                new BookingProperties(ZoneId.of("Asia/Ho_Chi_Minh")),
                new BookingHoldProperties(Duration.ofMinutes(10), Duration.ofSeconds(30)));
    }

    @Test
    void sortsSelectionsBeforeLockingAndCreatesOneBooking() {
        UUID customerId = UUID.randomUUID();
        UUID courtId = UUID.randomUUID();
        LocalDate date = LocalDate.now().plusDays(2);
        var morning = new CourtSlotSelection(date, LocalTime.of(8, 0), LocalTime.of(10, 0));
        var evening = new CourtSlotSelection(date, LocalTime.of(18, 0), LocalTime.of(20, 0));
        var request = new CreateBookingHoldRequest(courtId, List.of(
                slot(evening), slot(morning)));
        var idempotencyLock = mock(BookingLockManager.LockHandle.class);
        var slotLocks = mock(BookingLockManager.LockHandle.class);
        when(lockManager.acquireIdempotency(eq(customerId), anyString()))
                .thenReturn(idempotencyLock);
        when(lockManager.acquireSlots(any())).thenReturn(slotLocks);
        when(courtFacade.quoteBookableSlots(courtId, List.of(morning, evening)))
                .thenReturn(Optional.of(List.of(
                        quote(courtId, morning), quote(courtId, evening))));
        BookingResponse response = mock(BookingResponse.class);
        when(transaction.create(eq(customerId), eq("key-1"), anyString(),
                any(), any(), any(), any())).thenReturn(response);

        assertThat(service.createBooking(customerId, "key-1", request))
                .isSameAs(response);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(lockManager).acquireSlots(keys.capture());
        assertThat(keys.getValue()).containsExactly(
                "booking:slot:" + courtId + ":" + date + ":08:00:10:00",
                "booking:slot:" + courtId + ":" + date + ":18:00:20:00");
        verify(slotLocks).close();
        verify(idempotencyLock).close();
    }

    @Test
    void rejectsReusedIdempotencyKeyForDifferentSelection() {
        UUID customerId = UUID.randomUUID();
        UUID courtId = UUID.randomUUID();
        LocalDate date = LocalDate.now().plusDays(2);
        var request = new CreateBookingHoldRequest(courtId, List.of(
                new CreateBookingHoldRequest.Slot(
                        date, LocalTime.of(8, 0), LocalTime.of(10, 0))));
        var idempotencyLock = mock(BookingLockManager.LockHandle.class);
        when(lockManager.acquireIdempotency(eq(customerId), anyString()))
                .thenReturn(idempotencyLock);
        Booking prior = mock(Booking.class);
        when(bookingRepository.findByCustomerIdAndIdempotencyKey(customerId, "key-1"))
                .thenReturn(Optional.of(prior));

        assertThatThrownBy(() -> service.createBooking(customerId, "key-1", request))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(BookingErrorCode.IDEMPOTENCY_KEY_REUSED));
        verify(idempotencyLock).close();
    }

    @Test
    void replaysPreviouslyCommittedBookingWithoutLockingSlotsAgain() {
        UUID customerId = UUID.randomUUID();
        UUID courtId = UUID.randomUUID();
        LocalDate date = LocalDate.now().plusDays(2);
        var request = new CreateBookingHoldRequest(courtId, List.of(
                new CreateBookingHoldRequest.Slot(
                        date, LocalTime.of(8, 0), LocalTime.of(10, 0))));
        var idempotencyLock = mock(BookingLockManager.LockHandle.class);
        when(lockManager.acquireIdempotency(eq(customerId), anyString()))
                .thenReturn(idempotencyLock);
        Booking prior = mock(Booking.class);
        UUID bookingId = UUID.randomUUID();
        when(prior.hasIdempotencyRequestHash(anyString())).thenReturn(true);
        when(prior.getId()).thenReturn(bookingId);
        when(prior.getSlots()).thenReturn(List.of());
        when(bookingRepository.findByCustomerIdAndIdempotencyKey(customerId, "key-1"))
                .thenReturn(Optional.of(prior));

        var response = service.createBooking(customerId, "key-1", request);

        assertThat(response.id()).isEqualTo(bookingId);
        verify(lockManager, never()).acquireSlots(any());
        verify(idempotencyLock).close();
    }

    private CreateBookingHoldRequest.Slot slot(CourtSlotSelection selection) {
        return new CreateBookingHoldRequest.Slot(
                selection.date(), selection.startTime(), selection.endTime());
    }

    private CourtSlotQuote quote(UUID courtId, CourtSlotSelection selection) {
        return new CourtSlotQuote(
                courtId, selection.date(), selection.startTime(), selection.endTime(),
                new BigDecimal("200000"), "VND");
    }
}
