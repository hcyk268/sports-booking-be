package dev.booking.sports.court.api.dto.response;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;

import dev.booking.sports.court.domain.model.CourtPriceRule;

public record PriceRuleResponse(
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        BigDecimal pricePerHour) {

    public static PriceRuleResponse from(CourtPriceRule rule) {
        return new PriceRuleResponse(
                rule.getDayOfWeek(),
                rule.getStartTime(),
                rule.getEndTime(),
                rule.getPricePerHour());
    }
}