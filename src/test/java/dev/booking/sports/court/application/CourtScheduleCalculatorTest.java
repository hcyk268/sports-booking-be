package dev.booking.sports.court.application;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import dev.booking.sports.court.domain.model.CourtClosure;
import dev.booking.sports.court.domain.model.CourtOperatingHours;
import dev.booking.sports.court.domain.model.CourtPriceRule;

class CourtScheduleCalculatorTest {

    private final CourtScheduleCalculator calculator = new CourtScheduleCalculator();

    private CourtOperatingHours hours(String start, String end, int duration) {
        CourtOperatingHours value = new CourtOperatingHours();
        value.setOpeningTime(LocalTime.parse(start));
        value.setClosingTime(LocalTime.parse(end));
        value.setSlotDurationMinutes(duration);
        return value;
    }

    private CourtPriceRule price(String start, String end, String hourly) {
        CourtPriceRule value = new CourtPriceRule();
        value.setStartTime(LocalTime.parse(start));
        value.setEndTime(LocalTime.parse(end));
        value.setPricePerHour(new BigDecimal(hourly));
        return value;
    }

    private CourtClosure closure(String start, String end) {
        CourtClosure value = new CourtClosure();
        value.setStartTime(LocalTime.parse(start));
        value.setEndTime(LocalTime.parse(end));
        return value;
    }

    @Test
    void proratesShortSlotsAndCombinesPricesAcrossBoundary() {
        var slots = calculator.buildSlots(hours("16:30", "17:30", 60),
                List.of(price("06:00", "17:00", "100000"),
                        price("17:00", "22:00", "150000")), List.of());
        assertEquals(1, slots.size());
        assertEquals(new BigDecimal("125000"), slots.getFirst().totalPrice());

        var halfHour = calculator.buildSlots(hours("06:00", "07:00", 30),
                List.of(price("06:00", "07:00", "100000")), List.of());
        assertEquals(new BigDecimal("50000"), halfHour.getFirst().totalPrice());
    }

    @Test
    void combinesConsecutiveSlotsAndUsesTotalRangeBudget() {
        var slots = calculator.buildSlots(hours("16:00", "18:00", 60),
                List.of(price("06:00", "17:00", "100000"),
                        price("17:00", "22:00", "150000")), List.of());
        var match = calculator.findMatch(slots,
                LocalTime.parse("16:00"), LocalTime.parse("18:00"),
                new BigDecimal("250000"), new BigDecimal("250000"));
        assertTrue(match.isPresent());
        assertEquals(new BigDecimal("250000"), match.get().totalPrice());
        assertTrue(calculator.findMatch(slots,
                LocalTime.parse("16:00"), LocalTime.parse("18:00"),
                null, new BigDecimal("249999")).isEmpty());
    }

    @Test
    void excludesPartialClosuresButPreservesAdjacentSlots() {
        var slots = calculator.buildSlots(hours("09:00", "13:00", 60),
                List.of(price("09:00", "13:00", "100000")),
                List.of(closure("10:30", "11:30")));
        assertFalse(slots.get(0).closed());
        assertTrue(slots.get(1).closed());
        assertTrue(slots.get(2).closed());
        assertFalse(slots.get(3).closed());
        assertTrue(calculator.findMatch(slots,
                LocalTime.parse("10:00"), LocalTime.parse("12:00"),
                null, null).isEmpty());
    }

    @Test
    void refusesIncompletePriceCoverageInsteadOfTreatingItAsFree() {
        var slots = calculator.buildSlots(hours("06:00", "08:00", 60),
                List.of(price("06:00", "07:00", "100000")), List.of());
        assertNull(slots.get(1).totalPrice());
        assertFalse(slots.get(1).priceConfigured());
        assertTrue(calculator.findMatch(slots,
                LocalTime.parse("06:00"), LocalTime.parse("08:00"),
                null, null).isEmpty());
    }

    @Test
    void rejectsRangesNotAlignedWithSlotBoundaries() {
        var slots = calculator.buildSlots(hours("06:00", "08:00", 60),
                List.of(price("06:00", "08:00", "100000")), List.of());
        assertTrue(calculator.findMatch(slots,
                LocalTime.parse("06:30"), LocalTime.parse("07:30"),
                null, null).isEmpty());
    }

    @Test
    void dateOnlySearchChoosesEarliestSlotWithinBudget() {
        var slots = calculator.buildSlots(hours("16:00", "19:00", 60),
                List.of(price("06:00", "17:00", "100000"),
                        price("17:00", "22:00", "150000")), List.of());
        var match = calculator.findMatch(slots, null, null,
                new BigDecimal("150000"), new BigDecimal("150000")).orElseThrow();
        assertEquals(LocalTime.parse("17:00"), match.startTime());
    }

    @Test
    void closedDayAndIncompleteLastSlotProduceNoExtraSlot() {
        assertTrue(calculator.buildSlots(null, List.of(), List.of()).isEmpty());
        var slots = calculator.buildSlots(hours("06:00", "07:30", 60),
                List.of(price("06:00", "07:30", "100000")), List.of());
        assertEquals(1, slots.size());
        assertEquals(LocalTime.parse("07:00"), slots.getFirst().endTime());
    }
}
