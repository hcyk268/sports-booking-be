package dev.booking.sports.court.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import dev.booking.sports.court.api.dto.response.CourtResponse;
import dev.booking.sports.court.api.dto.response.CourtSearchResultResponse;
import dev.booking.sports.court.api.error.CourtErrorCode;
import dev.booking.sports.court.domain.repository.CourtClosureRepository;
import dev.booking.sports.court.domain.repository.CourtOperatingHoursRepository;
import dev.booking.sports.court.domain.repository.CourtPriceRuleRepository;
import dev.booking.sports.court.domain.repository.CourtRepository;
import dev.booking.sports.shared.api.PageResponse;
import dev.booking.sports.shared.exception.ApiException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CourtSearchService {

    private static final int BATCH_SIZE = 50;
    private final CourtRepository courtRepository;
    private final CourtOperatingHoursRepository hoursRepository;
    private final CourtPriceRuleRepository priceRuleRepository;
    private final CourtClosureRepository closureRepository;
    private final CourtScheduleCalculator calculator;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<CourtSearchResultResponse> search(
            String keyword, String city, UUID sportId,
            LocalDate date, LocalTime startTime, LocalTime endTime,
            BigDecimal minPrice, BigDecimal maxPrice, int page, int size) {
        validate(date, startTime, endTime, minPrice, maxPrice, page, size);
        var specification = CourtSpecifications.publicCourts(keyword, city, sportId);
        Sort order = Sort.by("name", "id");

        if (date == null) {
            var result = courtRepository.findAll(
                    specification, PageRequest.of(page, size, order));
            var items = result.getContent().stream()
                    .map(court -> new CourtSearchResultResponse(CourtResponse.from(court), null))
                    .toList();
            return PageResponse.of(items, page, size, result.getTotalElements());
        }

        List<CourtSearchResultResponse> items = new ArrayList<>();
        long skip = (long) page * size;
        long totalMatches = 0;
        int batchNumber = 0;

        while (true) {
            var candidates = courtRepository.findAll(
                    specification, PageRequest.of(batchNumber, BATCH_SIZE, order));
            if (candidates.isEmpty()) {
                break;
            }
            List<UUID> ids = candidates.getContent().stream()
                    .map(court -> court.getId()).toList();

            var hours = hoursRepository
                    .findByCourt_IdInAndDayOfWeek(ids, date.getDayOfWeek()).stream()
                    .collect(Collectors.toMap(
                            item -> item.getCourt().getId(), Function.identity()));
            var prices = priceRuleRepository
                    .findByCourt_IdInAndDayOfWeek(ids, date.getDayOfWeek()).stream()
                    .collect(Collectors.groupingBy(item -> item.getCourt().getId()));
            var closures = closureRepository
                    .findByCourt_IdInAndClosureDate(ids, date).stream()
                    .collect(Collectors.groupingBy(item -> item.getCourt().getId()));

            for (var court : candidates.getContent()) {
                UUID id = court.getId();
                var slots = calculator.buildSlots(
                        hours.get(id),
                        prices.getOrDefault(id, List.of()),
                        closures.getOrDefault(id, List.of()));
                var match = calculator.findMatch(
                        slots, startTime, endTime, minPrice, maxPrice);
                if (match.isEmpty()) {
                    continue;
                }

                long index = totalMatches++;
                if (index >= skip && items.size() < size) {
                    var selected = match.get();
                    items.add(new CourtSearchResultResponse(
                            CourtResponse.from(court),
                            new CourtSearchResultResponse.Match(
                                    date, selected.startTime(), selected.endTime(),
                                    selected.totalPrice(), "VND")));
                }
            }
            if (!candidates.hasNext()) {
                break;
            }
            batchNumber++;
        }

        return PageResponse.of(items, page, size, totalMatches);
    }

    private void validate(
            LocalDate date, LocalTime start, LocalTime end,
            BigDecimal minPrice, BigDecimal maxPrice, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(CourtErrorCode.INVALID_PAGINATION);
        }
        boolean timeFilter = start != null || end != null;
        boolean priceFilter = minPrice != null || maxPrice != null;
        if ((date == null && (timeFilter || priceFilter))
                || (timeFilter && (start == null || end == null))
                || (timeFilter && (!wholeMinute(start) || !wholeMinute(end)
                        || !start.isBefore(end)))
                || (minPrice != null && minPrice.signum() < 0)
                || (maxPrice != null && maxPrice.signum() < 0)
                || (minPrice != null && maxPrice != null
                        && minPrice.compareTo(maxPrice) > 0)) {
            throw new ApiException(CourtErrorCode.INVALID_COURT_SEARCH_FILTER);
        }
    }

    private boolean wholeMinute(LocalTime time) {
        return time.getSecond() == 0 && time.getNano() == 0;
    }
}
