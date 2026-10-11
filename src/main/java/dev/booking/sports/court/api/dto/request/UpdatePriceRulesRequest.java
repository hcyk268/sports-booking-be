package dev.booking.sports.court.api.dto.request;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdatePriceRulesRequest(
        @NotNull
        @Size(max = 100)
        List<@NotNull @Valid PriceRule> rules) {

    public record PriceRule(
            @NotNull DayOfWeek dayOfWeek,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @NotNull
            @DecimalMin(value = "0", inclusive = false)
            @Digits(integer = 10, fraction = 2)
            BigDecimal pricePerHour) {
    }
}