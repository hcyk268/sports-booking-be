package dev.booking.sports.court.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import dev.booking.sports.court.api.dto.response.CourtScheduleResponse;
import dev.booking.sports.court.api.error.CourtErrorCode;
import dev.booking.sports.court.domain.enums.CourtStatus;
import dev.booking.sports.court.domain.model.CourtOperatingHours;
import dev.booking.sports.court.domain.model.CourtPriceRule;
import dev.booking.sports.court.domain.repository.CourtOperatingHoursRepository;
import dev.booking.sports.court.domain.repository.CourtPriceRuleRepository;
import dev.booking.sports.court.domain.repository.CourtRepository;
import dev.booking.sports.shared.exception.ApiException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CourtScheduleService {

    private final CourtRepository courtRepository;
    private final CourtOperatingHoursRepository hoursRepository;
    private final CourtPriceRuleRepository priceRuleRepository;

    @Transactional(
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ)
    public CourtScheduleResponse getSchedule(
            UUID courtId,
            LocalDate date) {

        courtRepository
                .findByIdAndStatus(courtId, CourtStatus.ACTIVE)
                .orElseThrow(() ->
                        new ApiException(
                                CourtErrorCode.COURT_NOT_FOUND));

        CourtOperatingHours hours = hoursRepository
                .findByCourt_Id(courtId)
                .stream()
                .filter(item ->
                        item.getDayOfWeek() == date.getDayOfWeek())
                .findFirst()
                .orElse(null);

        if (hours == null) {
            return new CourtScheduleResponse(
                    courtId,
                    date,
                    "VND",
                    List.of());
        }

        List<CourtPriceRule> prices = priceRuleRepository
                .findByCourt_Id(courtId)
                .stream()
                .filter(item ->
                        item.getDayOfWeek() == date.getDayOfWeek())
                .sorted(Comparator.comparing(
                        CourtPriceRule::getStartTime))
                .toList();

        long operatingMinutes = Duration.between(
                hours.getOpeningTime(),
                hours.getClosingTime()).toMinutes();

        int slotMinutes = hours.getSlotDurationMinutes();
        long slotCount = operatingMinutes / slotMinutes;

        List<CourtScheduleResponse.Slot> slots =
                new ArrayList<>();

        for (long i = 0; i < slotCount; i++) {
            LocalTime start = hours.getOpeningTime()
                    .plusMinutes(i * slotMinutes);

            LocalTime end = start.plusMinutes(slotMinutes);

            BigDecimal totalPrice = calculatePrice(
                    start,
                    end,
                    prices);

            slots.add(new CourtScheduleResponse.Slot(
                    start,
                    end,
                    totalPrice,
                    totalPrice != null));
        }

        return new CourtScheduleResponse(
                courtId,
                date,
                "VND",
                slots);
    }

    private BigDecimal calculatePrice(
            LocalTime start,
            LocalTime end,
            List<CourtPriceRule> prices) {

        LocalTime cursor = start;

        BigDecimal weightedPrice = BigDecimal.ZERO;

        for (CourtPriceRule rule : prices) {
            if (!rule.getEndTime().isAfter(cursor)) {
                continue;
            }

            if (rule.getStartTime().isAfter(cursor)) {
                return null;
            }

            LocalTime segmentEnd =
                    rule.getEndTime().isBefore(end)
                            ? rule.getEndTime()
                            : end;

            long minutes = Duration.between(
                    cursor,
                    segmentEnd).toMinutes();

            weightedPrice = weightedPrice.add(
                    rule.getPricePerHour().multiply(
                            BigDecimal.valueOf(minutes)));

            cursor = segmentEnd;

            if (cursor.equals(end)) {
                return weightedPrice.divide(
                        BigDecimal.valueOf(60),
                        0,
                        RoundingMode.HALF_UP);
            }
        }

        return null;
    }
}