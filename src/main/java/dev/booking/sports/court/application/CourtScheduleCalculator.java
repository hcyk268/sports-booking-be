package dev.booking.sports.court.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import dev.booking.sports.court.api.dto.response.CourtScheduleResponse.Slot;
import dev.booking.sports.court.domain.model.CourtClosure;
import dev.booking.sports.court.domain.model.CourtOperatingHours;
import dev.booking.sports.court.domain.model.CourtPriceRule;

@Component
public class CourtScheduleCalculator {

    public List<Slot> buildSlots(
            CourtOperatingHours hours,
            List<CourtPriceRule> prices,
            List<CourtClosure> closures) {
        if (hours == null) {
            return List.of();
        }
        List<CourtPriceRule> orderedPrices = prices.stream()
                .sorted(Comparator.comparing(CourtPriceRule::getStartTime)).toList();
        long minutes = Duration.between(
                hours.getOpeningTime(), hours.getClosingTime()).toMinutes();
        int duration = hours.getSlotDurationMinutes();
        List<Slot> slots = new ArrayList<>();

        for (long i = 0; i < minutes / duration; i++) {
            LocalTime start = hours.getOpeningTime().plusMinutes(i * duration);
            LocalTime end = start.plusMinutes(duration);
            BigDecimal price = calculatePrice(start, end, orderedPrices);
            boolean closed = closures.stream().anyMatch(closure ->
                    start.isBefore(closure.getEndTime())
                    && end.isAfter(closure.getStartTime()));
            slots.add(new Slot(start, end, price, price != null, closed));
        }
        return slots;
    }

    public Optional<Slot> findMatch(
            List<Slot> slots,
            LocalTime start,
            LocalTime end,
            BigDecimal minPrice,
            BigDecimal maxPrice) {
        if (start == null) {
            return slots.stream()
                    .filter(slot -> !slot.closed() && slot.priceConfigured())
                    .filter(slot -> inBudget(slot.totalPrice(), minPrice, maxPrice))
                    .findFirst();
        }

        LocalTime cursor = start;
        BigDecimal total = BigDecimal.ZERO;
        for (Slot slot : slots) {
            if (!slot.endTime().isAfter(cursor)) {
                continue;
            }
            if (!slot.startTime().equals(cursor)
                    || slot.endTime().isAfter(end)
                    || slot.closed()
                    || !slot.priceConfigured()) {
                return Optional.empty();
            }
            total = total.add(slot.totalPrice());
            cursor = slot.endTime();
            if (cursor.equals(end)) {
                return inBudget(total, minPrice, maxPrice)
                        ? Optional.of(new Slot(start, end, total, true, false))
                        : Optional.empty();
            }
        }
        return Optional.empty();
    }

    private boolean inBudget(
            BigDecimal price, BigDecimal minPrice, BigDecimal maxPrice) {
        return (minPrice == null || price.compareTo(minPrice) >= 0)
                && (maxPrice == null || price.compareTo(maxPrice) <= 0);
    }

    private BigDecimal calculatePrice(
            LocalTime start, LocalTime end, List<CourtPriceRule> prices) {
        LocalTime cursor = start;
        BigDecimal weighted = BigDecimal.ZERO;
        for (CourtPriceRule rule : prices) {
            if (!rule.getEndTime().isAfter(cursor)) {
                continue;
            }
            if (rule.getStartTime().isAfter(cursor)) {
                return null;
            }
            LocalTime segmentEnd = rule.getEndTime().isBefore(end)
                    ? rule.getEndTime() : end;
            long minutes = Duration.between(cursor, segmentEnd).toMinutes();
            weighted = weighted.add(
                    rule.getPricePerHour().multiply(BigDecimal.valueOf(minutes)));
            cursor = segmentEnd;
            if (cursor.equals(end)) {
                return weighted.divide(BigDecimal.valueOf(60), 0, RoundingMode.HALF_UP);
            }
        }
        return null;
    }
}
