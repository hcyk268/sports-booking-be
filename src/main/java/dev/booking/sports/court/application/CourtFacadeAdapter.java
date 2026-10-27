package dev.booking.sports.court.application;

import java.util.Collection;
import java.util.ArrayList;
import java.time.LocalDate;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.booking.sports.court.domain.repository.CourtRepository;
import dev.booking.sports.court.api.dto.response.CourtScheduleResponse;
import dev.booking.sports.court.publicapi.CourtFacade;
import dev.booking.sports.court.publicapi.CourtSlotQuote;
import dev.booking.sports.court.publicapi.CourtSlotSelection;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class CourtFacadeAdapter implements CourtFacade {

    private final CourtRepository courtRepository;
    private final CourtScheduleService courtScheduleService;

    @Override
    public Optional<List<CourtSlotQuote>> quoteBookableSlots(UUID courtId, List<CourtSlotSelection> selections) {
        Map<LocalDate, CourtScheduleResponse> schedules = selections.stream()
                .map(CourtSlotSelection::date)
                .distinct()
                .collect(Collectors.toMap(Function.identity(),
                        date -> courtScheduleService.getSchedule(courtId, date)));

        List<CourtSlotQuote> quotes = new ArrayList<>(selections.size());
        for (CourtSlotSelection selection : selections) {
            var schedule = schedules.get(selection.date());
            var slot = schedule.slots().stream()
                    .filter(candidate -> candidate.startTime().equals(selection.startTime())
                            && candidate.endTime().equals(selection.endTime())
                            && candidate.priceConfigured()
                            && !candidate.closed())
                    .findFirst()
                    .orElse(null);
            if (slot == null) {
                return Optional.empty();
            }
            quotes.add(new CourtSlotQuote(
                    courtId,
                    selection.date(),
                    selection.startTime(),
                    selection.endTime(),
                    slot.totalPrice(),
                    schedule.currency()));
        }

        return Optional.of(List.copyOf(quotes));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> findOwnedCourtIds(UUID providerId) {
        return Set.copyOf(courtRepository.findIdsByOwnerId(providerId));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean ownsAllCourts(
            UUID providerId,
            Collection<UUID> courtIds) {
        if (courtIds == null || courtIds.isEmpty()) {
            return false;
        }

        Set<UUID> distinctCourtIds = Set.copyOf(courtIds);
        return courtRepository.countByOwnerIdAndIdIn(
                providerId,
                distinctCourtIds) == distinctCourtIds.size();
    }
}
