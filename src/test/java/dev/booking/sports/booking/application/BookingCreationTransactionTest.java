package dev.booking.sports.booking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import dev.booking.sports.booking.api.error.BookingErrorCode;
import dev.booking.sports.booking.domain.event.BookingHeldEvent;
import dev.booking.sports.booking.domain.event.BookingOutboxMessageTypes;
import dev.booking.sports.booking.domain.model.Booking;
import dev.booking.sports.booking.domain.repository.BookingRepository;
import dev.booking.sports.booking.domain.repository.BookingSlotRepository;
import dev.booking.sports.court.publicapi.CourtSlotQuote;
import dev.booking.sports.shared.exception.ApiException;
import dev.booking.sports.shared.outbox.OutboxService;

@ExtendWith(MockitoExtension.class)
class BookingCreationTransactionTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private BookingSlotRepository bookingSlotRepository;
    @Mock private OutboxService outboxService;

    private BookingCreationTransaction transaction;

    @BeforeEach
    void setUp() {
        transaction = new BookingCreationTransaction(
                bookingRepository, bookingSlotRepository, outboxService);
    }

    @Test
    void savesHeldBookingAndOutboxSnapshot() {
        UUID customerId = UUID.randomUUID();
        UUID courtId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-25T01:00:00Z");
        Instant expiry = now.plusSeconds(600);
        LocalDate date = LocalDate.of(2026, 10, 26);
        CourtSlotQuote quote = new CourtSlotQuote(
                courtId, date, LocalTime.of(8, 0), LocalTime.of(10, 0),
                new BigDecimal("200000"), "VND");
        when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenAnswer(invocation -> {
                    Booking booking = invocation.getArgument(0);
                    ReflectionTestUtils.setField(booking, "id", UUID.randomUUID());
                    booking.getSlots().forEach(slot ->
                            ReflectionTestUtils.setField(slot, "id", UUID.randomUUID()));
                    return booking;
                });

        var response = transaction.create(
                customerId, "key-1", "a".repeat(64), List.of(quote),
                ZoneId.of("Asia/Ho_Chi_Minh"), now, expiry);

        assertThat(response.customerId()).isEqualTo(customerId);
        assertThat(response.status().name()).isEqualTo("HELD");
        assertThat(response.totalAmount()).isEqualByComparingTo("200000");
        assertThat(response.slots()).hasSize(1);
        ArgumentCaptor<BookingHeldEvent> event =
                ArgumentCaptor.forClass(BookingHeldEvent.class);
        verify(outboxService).enqueue(
                eq(BookingOutboxMessageTypes.BOOKING_AGGREGATE_TYPE),
                eq(response.id()),
                eq(BookingOutboxMessageTypes.BOOKING_HELD),
                eq(BookingOutboxMessageTypes.EVENT_VERSION),
                event.capture());
        assertThat(event.getValue().slots().getFirst().bookingSlotId())
                .isEqualTo(response.slots().getFirst().id());
        assertThat(event.getValue().holdExpiresAt()).isEqualTo(expiry);
    }

    @Test
    void rejectsOverlapBeforeWritingBookingOrOutbox() {
        UUID customerId = UUID.randomUUID();
        UUID courtId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-25T01:00:00Z");
        LocalDate date = LocalDate.of(2026, 10, 26);
        CourtSlotQuote quote = new CourtSlotQuote(
                courtId, date, LocalTime.of(8, 0), LocalTime.of(10, 0),
                new BigDecimal("200000"), "VND");
        when(bookingSlotRepository.findOverlapping(
                eq(courtId), any(), any(), any()))
                .thenReturn(List.of(org.mockito.Mockito.mock(
                        dev.booking.sports.booking.domain.model.BookingSlot.class)));

        assertThatThrownBy(() -> transaction.create(
                customerId, "key-1", "a".repeat(64), List.of(quote),
                ZoneId.of("Asia/Ho_Chi_Minh"), now, now.plusSeconds(600)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(BookingErrorCode.BOOKING_SLOT_UNAVAILABLE));
        verify(bookingRepository, never()).saveAndFlush(any());
        verify(outboxService, never()).enqueue(any(), any(), any(),
                org.mockito.ArgumentMatchers.anyInt(), any());
    }
}
