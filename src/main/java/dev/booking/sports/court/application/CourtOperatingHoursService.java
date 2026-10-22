package dev.booking.sports.court.application;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.booking.sports.court.api.dto.request.UpdateOperatingHoursRequest;
import dev.booking.sports.court.api.dto.response.OperatingHoursResponse;
import dev.booking.sports.court.api.error.CourtErrorCode;
import dev.booking.sports.court.domain.model.Court;
import dev.booking.sports.court.domain.model.CourtOperatingHours;
import dev.booking.sports.court.domain.repository.CourtOperatingHoursRepository;
import dev.booking.sports.court.domain.repository.CourtRepository;
import dev.booking.sports.shared.exception.ApiException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CourtOperatingHoursService {

    private final CourtRepository courtRepository;
    private final CourtOperatingHoursRepository hoursRepository;

    @Transactional(readOnly = true)
    public List<OperatingHoursResponse> getOperatingHours(
            UUID ownerId,
            UUID courtId) {

        requireOwnedCourt(ownerId, courtId);

        return toResponses(
                hoursRepository.findByCourt_Id(courtId));
    }

    @Transactional
    public List<OperatingHoursResponse> updateOperatingHours(
            UUID ownerId,
            UUID courtId,
            UpdateOperatingHoursRequest request) {

        Court court = requireOwnedCourtForUpdate(ownerId, courtId);

        validateSchedule(request);

        List<CourtOperatingHours> existing =
                hoursRepository.findByCourt_Id(courtId);

        hoursRepository.deleteAll(existing);
        hoursRepository.flush();

        List<CourtOperatingHours> replacement = new ArrayList<>();

        for (var day : request.days()) {
            CourtOperatingHours hours = new CourtOperatingHours();
            hours.setCourt(court);
            hours.setDayOfWeek(day.dayOfWeek());
            hours.setOpeningTime(day.openingTime());
            hours.setClosingTime(day.closingTime());
            hours.setSlotDurationMinutes(day.slotDurationMinutes());

            replacement.add(hours);
        }

        return toResponses(
                hoursRepository.saveAll(replacement));
    }

    private Court requireOwnedCourt(
            UUID ownerId,
            UUID courtId) {

        return courtRepository
                .findByIdAndOwnerId(courtId, ownerId)
                .orElseThrow(() ->
                        new ApiException(
                                CourtErrorCode.COURT_NOT_FOUND));
    }

    private Court requireOwnedCourtForUpdate(
            UUID ownerId,
            UUID courtId) {

        return courtRepository
                .findOwnedCourtForUpdate(courtId, ownerId)
                .orElseThrow(() ->
                        new ApiException(
                                CourtErrorCode.COURT_NOT_FOUND));
    }

    private void validateSchedule(
            UpdateOperatingHoursRequest request) {

        EnumSet<DayOfWeek> seenDays =
                EnumSet.noneOf(DayOfWeek.class);

        for (var day : request.days()) {
            if (!seenDays.add(day.dayOfWeek())) {
                throw new ApiException(
                        CourtErrorCode.DUPLICATE_OPERATING_DAY);
            }

            LocalTime opening = day.openingTime();
            LocalTime closing = day.closingTime();

            if (!isWholeMinute(opening)
                    || !isWholeMinute(closing)
                    || !opening.isBefore(closing)
                    || Duration.between(opening, closing).toMinutes()
                            < day.slotDurationMinutes()) {

                throw new ApiException(
                        CourtErrorCode.INVALID_OPERATING_HOURS);
            }
        }
    }

    private boolean isWholeMinute(LocalTime time) {
        return time.getSecond() == 0 && time.getNano() == 0;
    }

    private List<OperatingHoursResponse> toResponses(
            List<CourtOperatingHours> hours) {

        return hours.stream()
                .sorted(Comparator.comparingInt(
                        item -> item.getDayOfWeek().getValue()))
                .map(OperatingHoursResponse::from)
                .toList();
    }
}
