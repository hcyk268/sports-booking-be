package dev.booking.sports.court.application;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import dev.booking.sports.court.api.dto.response.CourtScheduleResponse;
import dev.booking.sports.court.api.error.CourtErrorCode;
import dev.booking.sports.court.domain.enums.CourtStatus;
import dev.booking.sports.court.domain.repository.CourtClosureRepository;
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
    private final CourtClosureRepository closureRepository;
    private final CourtScheduleCalculator calculator;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CourtScheduleResponse getSchedule(UUID courtId, LocalDate date) {
        courtRepository.findByIdAndStatus(courtId, CourtStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(CourtErrorCode.COURT_NOT_FOUND));

        var hours = hoursRepository.findByCourt_Id(courtId).stream()
                .filter(item -> item.getDayOfWeek() == date.getDayOfWeek())
                .findFirst().orElse(null);
        var prices = priceRuleRepository.findByCourt_Id(courtId).stream()
                .filter(item -> item.getDayOfWeek() == date.getDayOfWeek()).toList();
        var closures = closureRepository
                .findByCourt_IdAndClosureDateOrderByStartTimeAsc(courtId, date);

        return new CourtScheduleResponse(
                courtId, date, "VND", calculator.buildSlots(hours, prices, closures));
    }
}
