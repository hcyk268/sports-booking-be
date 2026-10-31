package dev.booking.sports.booking.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import dev.booking.sports.booking.api.error.BookingErrorCode;
import dev.booking.sports.booking.domain.enums.*;
import dev.booking.sports.booking.domain.model.*;
import dev.booking.sports.booking.domain.repository.BookingRepository;
import dev.booking.sports.shared.exception.ApiException;
import dev.booking.sports.court.publicapi.CourtFacade;

class BookingPaymentFacadeAdapterTest {
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

    @Test
    void startsBookingAndSlotsOnceWithoutExtendingHold() {
        Booking booking = booking();
        BookingRepository repository = mock(BookingRepository.class);
        when(repository.findOwnedByIdForUpdate(booking.getId(), booking.getCustomerId())).thenReturn(Optional.of(booking));
        var adapter = new BookingPaymentFacadeAdapter(repository, org.mockito.Mockito.mock(dev.booking.sports.court.publicapi.CourtFacade.class), org.mockito.Mockito.mock(dev.booking.sports.shared.outbox.OutboxService.class));
        var snapshot = adapter.lockOwnedBooking(booking.getCustomerId(), booking.getId());
        adapter.startPayment(booking.getCustomerId(), booking.getId(), NOW.plusSeconds(1));
        adapter.startPayment(booking.getCustomerId(), booking.getId(), NOW.plusSeconds(2));
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(booking.getSlots()).allSatisfy(slot -> assertThat(slot.getStatus()).isEqualTo(BookingSlotStatus.PENDING_PAYMENT));
        assertThat(booking.getStatusHistory()).hasSize(2);
        assertThat(booking.getHoldExpiresAt()).isEqualTo(snapshot.holdExpiresAt());
    }

    @Test
    void inaccessibleBookingIsNotDisclosed() {
        var adapter = new BookingPaymentFacadeAdapter(mock(BookingRepository.class), mock(dev.booking.sports.court.publicapi.CourtFacade.class), mock(dev.booking.sports.shared.outbox.OutboxService.class));
        assertThatThrownBy(() -> adapter.lockOwnedBooking(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.getErrorCode())
                    .isEqualTo(BookingErrorCode.BOOKING_NOT_FOUND));
    }

    @Test
    void exactDeadlineCannotStartPayment() {
        Booking booking = booking();
        BookingRepository repository = mock(BookingRepository.class);
        when(repository.findOwnedByIdForUpdate(booking.getId(), booking.getCustomerId())).thenReturn(Optional.of(booking));
        var adapter = new BookingPaymentFacadeAdapter(repository, org.mockito.Mockito.mock(dev.booking.sports.court.publicapi.CourtFacade.class), org.mockito.Mockito.mock(dev.booking.sports.shared.outbox.OutboxService.class));
        assertThatThrownBy(() -> adapter.startPayment(booking.getCustomerId(), booking.getId(), booking.getHoldExpiresAt()))
                .isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.getErrorCode())
                    .isEqualTo(BookingErrorCode.BOOKING_PAYMENT_NOT_ALLOWED));
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.HELD);
    }

    private Booking booking() {
        Booking booking = Booking.hold("BK001", UUID.randomUUID(), "VND", "booking-key", "a".repeat(64),
                NOW.plusSeconds(600), NOW, List.of(new BookingSlot.Spec(UUID.randomUUID(),
                    NOW.plusSeconds(3600), NOW.plusSeconds(7200), new BigDecimal("300000"))));
        booking.setId(UUID.randomUUID());
        return booking;
    }

    @Test
    void readAccessSupportsOwnerAdminAndCourtProviderAndHidesOtherCustomers() {
        Booking booking = booking();
        BookingRepository repository = mock(BookingRepository.class);
        CourtFacade courts = mock(CourtFacade.class);
        var adapter = new BookingPaymentFacadeAdapter(repository, courts, mock(dev.booking.sports.shared.outbox.OutboxService.class));
        when(repository.findWithSlotsById(booking.getId())).thenReturn(Optional.of(booking));
        assertThat(adapter.getAccessibleBooking(booking.getCustomerId(), Set.of("ROLE_CUSTOMER"), booking.getId())
                .bookingId()).isEqualTo(booking.getId());
        assertThat(adapter.getAccessibleBooking(UUID.randomUUID(), Set.of("ROLE_ADMIN"), booking.getId())
                .awaitingPayment()).isTrue();
        UUID provider = UUID.randomUUID();
        var courtIds = booking.getSlots().stream().map(BookingSlot::getCourtId).toList();
        when(courts.ownsAllCourts(provider, courtIds)).thenReturn(true);
        assertThat(adapter.getAccessibleBooking(provider, Set.of("ROLE_PROVIDER"), booking.getId()).bookingId())
                .isEqualTo(booking.getId());
        assertThatThrownBy(() -> adapter.getAccessibleBooking(UUID.randomUUID(), Set.of("ROLE_CUSTOMER"), booking.getId()))
                .isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.getErrorCode())
                    .isEqualTo(BookingErrorCode.BOOKING_NOT_FOUND));
        assertThatThrownBy(() -> adapter.getAccessibleBooking(UUID.randomUUID(), Set.of("ROLE_PROVIDER"), booking.getId()))
                .isInstanceOf(ApiException.class);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.HELD);
        verify(repository, never()).findByIdForUpdate(any());
    }

    @Test
    void batchReadLoadsBookingsAndCourtOwnershipOnceAndCombinesRoles() {
        var ownBooking = booking();
        var courtBooking = booking();
        UUID actor = ownBooking.getCustomerId();
        var ids = Set.of(ownBooking.getId(), courtBooking.getId());
        BookingRepository repository = mock(BookingRepository.class);
        CourtFacade courts = mock(CourtFacade.class);
        when(repository.findWithSlotsByIdIn(ids)).thenReturn(List.of(courtBooking, ownBooking));
        when(courts.findOwnedCourtIds(actor)).thenReturn(Set.of(courtBooking.getSlots().getFirst().getCourtId()));
        var adapter = new BookingPaymentFacadeAdapter(repository, courts, mock(dev.booking.sports.shared.outbox.OutboxService.class));

        var result = adapter.getAccessibleBookings(actor, Set.of("ROLE_CUSTOMER", "ROLE_PROVIDER"), ids);

        assertThat(result).containsOnlyKeys(ownBooking.getId(), courtBooking.getId());
        assertThat(result.get(courtBooking.getId()).awaitingPayment()).isTrue();
        verify(repository).findWithSlotsByIdIn(ids);
        verify(courts).findOwnedCourtIds(actor);
        verifyNoMoreInteractions(repository, courts);
    }

    @Test
    void batchReadRequiresProviderToOwnEveryCourtAndHidesMissingBookings() {
        UUID actor = UUID.randomUUID();
        UUID firstCourt = UUID.randomUUID();
        UUID secondCourt = UUID.randomUUID();
        var booking = Booking.hold("BK002", UUID.randomUUID(), "VND", "batch-key", "a".repeat(64),
                NOW.plusSeconds(600), NOW, List.of(
                    new BookingSlot.Spec(firstCourt, NOW.plusSeconds(3600), NOW.plusSeconds(7200), BigDecimal.TEN),
                    new BookingSlot.Spec(secondCourt, NOW.plusSeconds(3600), NOW.plusSeconds(7200), BigDecimal.TEN)));
        booking.setId(UUID.randomUUID());
        var ids = Set.of(booking.getId());
        BookingRepository repository = mock(BookingRepository.class);
        CourtFacade courts = mock(CourtFacade.class);
        when(repository.findWithSlotsByIdIn(ids)).thenReturn(List.of(booking));
        when(courts.findOwnedCourtIds(actor)).thenReturn(Set.of(firstCourt));
        var adapter = new BookingPaymentFacadeAdapter(repository, courts, mock(dev.booking.sports.shared.outbox.OutboxService.class));

        assertThatThrownBy(() -> adapter.getAccessibleBookings(actor, Set.of("ROLE_PROVIDER"), ids))
                .isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.getErrorCode())
                    .isEqualTo(BookingErrorCode.BOOKING_NOT_FOUND));
        when(courts.findOwnedCourtIds(actor)).thenReturn(Set.of(firstCourt, secondCourt));
        assertThat(adapter.getAccessibleBookings(actor, Set.of("ROLE_PROVIDER"), ids)).containsOnlyKeys(booking.getId());
        assertThat(adapter.getAccessibleBookings(actor, Set.of("ROLE_ADMIN"), ids)).containsOnlyKeys(booking.getId());
        when(repository.findWithSlotsByIdIn(ids)).thenReturn(List.of());
        assertThatThrownBy(() -> adapter.getAccessibleBookings(actor, Set.of("ROLE_ADMIN"), ids))
                .isInstanceOfSatisfying(ApiException.class, exception -> assertThat(exception.getErrorCode())
                    .isEqualTo(BookingErrorCode.BOOKING_NOT_FOUND));
    }

    @Test
    void emptyBatchDoesNotQueryDatabase() {
        BookingRepository repository = mock(BookingRepository.class);
        CourtFacade courts = mock(CourtFacade.class);
        var adapter = new BookingPaymentFacadeAdapter(repository, courts, mock(dev.booking.sports.shared.outbox.OutboxService.class));
        assertThat(adapter.getAccessibleBookings(UUID.randomUUID(), Set.of("ROLE_ADMIN"), List.of())).isEmpty();
        verifyNoInteractions(repository, courts);
    }
}
