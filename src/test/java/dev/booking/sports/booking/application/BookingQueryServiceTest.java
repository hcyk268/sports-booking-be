package dev.booking.sports.booking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import dev.booking.sports.booking.api.error.BookingErrorCode;
import dev.booking.sports.booking.config.BookingProperties;
import dev.booking.sports.booking.domain.enums.BookingSlotStatus;
import dev.booking.sports.booking.domain.enums.BookingStatus;
import dev.booking.sports.booking.domain.model.Booking;
import dev.booking.sports.booking.domain.model.BookingSlot;
import dev.booking.sports.booking.domain.repository.BookingRepository;
import dev.booking.sports.court.publicapi.CourtFacade;
import dev.booking.sports.shared.exception.ApiException;

@ExtendWith(MockitoExtension.class)
class BookingQueryServiceTest {

    private static final ZoneId BOOKING_ZONE =
            ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private CourtFacade courtFacade;

    private BookingQueryService bookingQueryService;

    @BeforeEach
    void setUp() {
        bookingQueryService = new BookingQueryService(
                bookingRepository,
                new BookingProperties(BOOKING_ZONE),
                courtFacade);
    }

    @Test
    void listsOwnedBookingsWithStatusAndLocalDateFilters() {
        UUID customerId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        UUID courtId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 10, 23);
        Instant slotStart = Instant.parse("2026-10-23T01:00:00Z");
        Instant slotEnd = Instant.parse("2026-10-23T03:00:00Z");

        Booking booking = mock(Booking.class);
        BookingSlot slot = mock(BookingSlot.class);
        when(booking.getId()).thenReturn(bookingId);
        when(booking.getBookingCode()).thenReturn("BK-20261023-001");
        when(booking.getCustomerId()).thenReturn(customerId);
        when(booking.getTotalAmount()).thenReturn(new BigDecimal("200000.00"));
        when(booking.getCurrency()).thenReturn("VND");
        when(booking.getStatus()).thenReturn(BookingStatus.CONFIRMED);
        when(booking.getVersion()).thenReturn(1L);
        when(booking.getCreatedAt()).thenReturn(Instant.parse("2026-10-22T12:00:00Z"));
        when(booking.getSlots()).thenReturn(List.of(slot));
        when(slot.getId()).thenReturn(slotId);
        when(slot.getCourtId()).thenReturn(courtId);
        when(slot.getStartAt()).thenReturn(slotStart);
        when(slot.getEndAt()).thenReturn(slotEnd);
        when(slot.getPriceAmount()).thenReturn(new BigDecimal("200000.00"));
        when(slot.getCurrency()).thenReturn("VND");
        when(slot.getStatus()).thenReturn(BookingSlotStatus.CONFIRMED);
        when(slot.getVersion()).thenReturn(0L);

        Pageable requestedPage = Pageable.ofSize(20);
        when(bookingRepository.findCustomerBookings(
                eq(customerId),
                eq(BookingStatus.CONFIRMED),
                any(Instant.class),
                any(Instant.class),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(
                        List.of(booking),
                        requestedPage,
                        1));
        when(bookingRepository.findWithSlotsByIdIn(List.of(bookingId)))
                .thenReturn(List.of(booking));

        var response = bookingQueryService.listMyBookings(
                customerId,
                BookingStatus.CONFIRMED,
                date,
                0,
                20);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().id()).isEqualTo(bookingId);
        assertThat(response.items().getFirst().customerId()).isEqualTo(customerId);
        assertThat(response.items().getFirst().slots())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.id()).isEqualTo(slotId);
                    assertThat(item.courtId()).isEqualTo(courtId);
                });
        assertThat(response.pagination().totalElements()).isEqualTo(1);

        ArgumentCaptor<Instant> startCaptor =
                ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> endCaptor =
                ArgumentCaptor.forClass(Instant.class);
        verify(bookingRepository).findCustomerBookings(
                eq(customerId),
                eq(BookingStatus.CONFIRMED),
                startCaptor.capture(),
                endCaptor.capture(),
                any(Pageable.class));
        assertThat(startCaptor.getValue())
                .isEqualTo(date.atStartOfDay(BOOKING_ZONE).toInstant());
        assertThat(endCaptor.getValue())
                .isEqualTo(date.plusDays(1)
                        .atStartOfDay(BOOKING_ZONE)
                        .toInstant());
    }

    @Test
    void skipsSlotQueryWhenPageIsEmpty() {
        UUID customerId = UUID.randomUUID();
        when(bookingRepository.findCustomerBookings(
                eq(customerId),
                eq(null),
                eq(null),
                eq(null),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        var response = bookingQueryService.listMyBookings(
                customerId, null, null, 0, 20);

        assertThat(response.items()).isEmpty();
        verify(bookingRepository, never()).findWithSlotsByIdIn(any());
    }

    @Test
    void rejectsInvalidPagination() {
        assertThatThrownBy(() -> bookingQueryService.listMyBookings(
                UUID.randomUUID(), null, null, -1, 20))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(BookingErrorCode.INVALID_BOOKING_PAGINATION));

        verify(bookingRepository, never()).findCustomerBookings(
                any(), any(), any(), any(), any());
    }

    @Test
    void listsAllMatchingBookingsForAdmin() {
        UUID adminId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID courtId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 10, 24);
        when(bookingRepository.findBookings(
                eq(customerId),
                eq(courtId),
                eq(BookingStatus.CONFIRMED),
                any(Instant.class),
                any(Instant.class),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        var response = bookingQueryService.listBookings(
                new BookingViewer(adminId, Set.of(BookingActorRole.ADMIN)),
                customerId,
                courtId,
                BookingStatus.CONFIRMED,
                date,
                0,
                20);

        assertThat(response.items()).isEmpty();
        verify(courtFacade, never()).findOwnedCourtIds(any());

        ArgumentCaptor<Instant> startCaptor =
                ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> endCaptor =
                ArgumentCaptor.forClass(Instant.class);
        verify(bookingRepository).findBookings(
                eq(customerId),
                eq(courtId),
                eq(BookingStatus.CONFIRMED),
                startCaptor.capture(),
                endCaptor.capture(),
                any(Pageable.class));
        assertThat(startCaptor.getValue())
                .isEqualTo(date.atStartOfDay(BOOKING_ZONE).toInstant());
        assertThat(endCaptor.getValue())
                .isEqualTo(date.plusDays(1)
                        .atStartOfDay(BOOKING_ZONE)
                        .toInstant());
    }

    @Test
    void limitsProviderListToBookingsWhoseCourtsAreAllOwned() {
        UUID providerId = UUID.randomUUID();
        UUID firstCourtId = UUID.randomUUID();
        UUID secondCourtId = UUID.randomUUID();
        Set<UUID> ownedCourtIds = Set.of(firstCourtId, secondCourtId);
        when(courtFacade.findOwnedCourtIds(providerId))
                .thenReturn(ownedCourtIds);
        when(bookingRepository.findProviderBookings(
                eq(ownedCourtIds),
                eq(null),
                eq(firstCourtId),
                eq(null),
                eq(null),
                eq(null),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        var response = bookingQueryService.listBookings(
                new BookingViewer(providerId, Set.of(BookingActorRole.PROVIDER)),
                null,
                firstCourtId,
                null,
                null,
                0,
                20);

        assertThat(response.items()).isEmpty();
        verify(bookingRepository).findProviderBookings(
                eq(ownedCourtIds),
                eq(null),
                eq(firstCourtId),
                eq(null),
                eq(null),
                eq(null),
                any(Pageable.class));
    }

    @Test
    void returnsEmptyPageWhenProviderDoesNotOwnRequestedCourt() {
        UUID providerId = UUID.randomUUID();
        when(courtFacade.findOwnedCourtIds(providerId))
                .thenReturn(Set.of(UUID.randomUUID()));

        var response = bookingQueryService.listBookings(
                new BookingViewer(providerId, Set.of(BookingActorRole.PROVIDER)),
                null,
                UUID.randomUUID(),
                null,
                null,
                3,
                10);

        assertThat(response.items()).isEmpty();
        assertThat(response.pagination().page()).isEqualTo(3);
        assertThat(response.pagination().size()).isEqualTo(10);
        assertThat(response.pagination().totalElements()).isZero();
        verify(bookingRepository, never()).findProviderBookings(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsBookingListForViewerWithoutManagementRole() {
        assertThatThrownBy(() -> bookingQueryService.listBookings(
                new BookingViewer(
                        UUID.randomUUID(),
                        Set.of(BookingActorRole.CUSTOMER)),
                null,
                null,
                null,
                null,
                0,
                20))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(BookingErrorCode.BOOKING_ACCESS_DENIED));
    }

    @Test
    void returnsBookingOwnedByCustomer() {
        UUID customerId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Booking booking = mockBooking(
                bookingId,
                customerId,
                UUID.randomUUID());
        when(bookingRepository.findWithSlotsById(bookingId))
                .thenReturn(Optional.of(booking));

        var response = bookingQueryService.getBooking(
                new BookingViewer(
                        customerId,
                        Set.of(BookingActorRole.CUSTOMER)),
                bookingId);

        assertThat(response.id()).isEqualTo(bookingId);
        verify(courtFacade, never()).ownsAllCourts(any(), any());
    }

    @Test
    void returnsBookingWhenProviderOwnsAllCourts() {
        UUID providerId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID firstCourtId = UUID.randomUUID();
        UUID secondCourtId = UUID.randomUUID();
        Booking booking = mockBooking(
                bookingId,
                UUID.randomUUID(),
                firstCourtId,
                secondCourtId);
        when(bookingRepository.findWithSlotsById(bookingId))
                .thenReturn(Optional.of(booking));
        when(courtFacade.ownsAllCourts(
                eq(providerId),
                eq(List.of(firstCourtId, secondCourtId))))
                .thenReturn(true);

        var response = bookingQueryService.getBooking(
                new BookingViewer(
                        providerId,
                        Set.of(BookingActorRole.PROVIDER)),
                bookingId);

        assertThat(response.id()).isEqualTo(bookingId);
    }

    @Test
    void hidesBookingFromUnauthorizedCustomer() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = mock(Booking.class);
        when(booking.getCustomerId()).thenReturn(UUID.randomUUID());
        when(bookingRepository.findWithSlotsById(bookingId))
                .thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingQueryService.getBooking(
                new BookingViewer(
                        UUID.randomUUID(),
                        Set.of(BookingActorRole.CUSTOMER)),
                bookingId))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(BookingErrorCode.BOOKING_NOT_FOUND));
    }

    @Test
    void returnsNotFoundWhenBookingDoesNotExist() {
        UUID bookingId = UUID.randomUUID();
        when(bookingRepository.findWithSlotsById(bookingId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingQueryService.getBooking(
                new BookingViewer(
                        UUID.randomUUID(),
                        Set.of(BookingActorRole.ADMIN)),
                bookingId))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(BookingErrorCode.BOOKING_NOT_FOUND));
    }

    private Booking mockBooking(
            UUID bookingId,
            UUID customerId,
            UUID... courtIds) {
        Booking booking = mock(Booking.class);
        List<BookingSlot> slots = java.util.Arrays.stream(courtIds)
                .map(courtId -> {
                    BookingSlot slot = mock(BookingSlot.class);
                    when(slot.getCourtId()).thenReturn(courtId);
                    return slot;
                })
                .toList();
        when(booking.getId()).thenReturn(bookingId);
        when(booking.getCustomerId()).thenReturn(customerId);
        when(booking.getSlots()).thenReturn(slots);
        return booking;
    }
}
