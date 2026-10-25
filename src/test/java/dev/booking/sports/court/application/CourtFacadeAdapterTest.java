package dev.booking.sports.court.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.booking.sports.court.domain.repository.CourtRepository;
import dev.booking.sports.court.api.dto.response.CourtScheduleResponse;
import dev.booking.sports.court.publicapi.CourtSlotSelection;

@ExtendWith(MockitoExtension.class)
class CourtFacadeAdapterTest {

    @Mock
    private CourtRepository courtRepository;
    @Mock
    private CourtScheduleService scheduleService;

    private CourtFacadeAdapter courtFacade;

    @BeforeEach
    void setUp() {
        courtFacade = new CourtFacadeAdapter(courtRepository, scheduleService);
    }

    @Test
    void returnsTrueOnlyWhenProviderOwnsEveryDistinctCourt() {
        UUID providerId = UUID.randomUUID();
        UUID firstCourtId = UUID.randomUUID();
        UUID secondCourtId = UUID.randomUUID();
        when(courtRepository.countByOwnerIdAndIdIn(
                providerId,
                java.util.Set.of(firstCourtId, secondCourtId)))
                .thenReturn(2L);

        boolean owned = courtFacade.ownsAllCourts(
                providerId,
                List.of(firstCourtId, firstCourtId, secondCourtId));

        assertThat(owned).isTrue();
    }

    @Test
    void returnsImmutableOwnedCourtIds() {
        UUID providerId = UUID.randomUUID();
        UUID firstCourtId = UUID.randomUUID();
        UUID secondCourtId = UUID.randomUUID();
        when(courtRepository.findIdsByOwnerId(providerId))
                .thenReturn(List.of(firstCourtId, secondCourtId));

        var courtIds = courtFacade.findOwnedCourtIds(providerId);

        assertThat(courtIds).containsExactlyInAnyOrder(
                firstCourtId,
                secondCourtId);
        assertThatThrownBy(() -> courtIds.add(UUID.randomUUID()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void returnsFalseForEmptyCourtCollection() {
        assertThat(courtFacade.ownsAllCourts(
                UUID.randomUUID(),
                List.of())).isFalse();

        verify(courtRepository, never())
                .countByOwnerIdAndIdIn(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void quotesOnlyExactOpenPricedSlotsAndLoadsScheduleOncePerDate() {
        UUID courtId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 10, 26);
        LocalTime first = LocalTime.of(8, 0);
        LocalTime middle = LocalTime.of(10, 0);
        LocalTime last = LocalTime.of(12, 0);
        when(scheduleService.getSchedule(courtId, date)).thenReturn(
                new CourtScheduleResponse(courtId, date, "VND", List.of(
                        new CourtScheduleResponse.Slot(
                                first, middle, new BigDecimal("200000"), true, false),
                        new CourtScheduleResponse.Slot(
                                middle, last, new BigDecimal("250000"), true, false))));

        var quotes = courtFacade.quoteBookableSlots(courtId, List.of(
                new CourtSlotSelection(date, first, middle),
                new CourtSlotSelection(date, middle, last)));

        assertThat(quotes).isPresent();
        assertThat(quotes.orElseThrow()).hasSize(2);
        assertThat(quotes.orElseThrow().get(1).priceAmount())
                .isEqualByComparingTo("250000");
        verify(scheduleService).getSchedule(courtId, date);
    }
}
