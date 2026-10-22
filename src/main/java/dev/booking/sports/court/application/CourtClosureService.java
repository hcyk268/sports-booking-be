package dev.booking.sports.court.application;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.booking.sports.court.api.dto.request.CreateCourtClosureRequest;
import dev.booking.sports.court.api.dto.response.CourtClosureResponse;
import dev.booking.sports.court.api.error.CourtErrorCode;
import dev.booking.sports.court.domain.model.Court;
import dev.booking.sports.court.domain.model.CourtClosure;
import dev.booking.sports.court.domain.repository.CourtClosureRepository;
import dev.booking.sports.court.domain.repository.CourtRepository;
import dev.booking.sports.shared.exception.ApiException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CourtClosureService {

    private final CourtRepository courtRepository;
    private final CourtClosureRepository closureRepository;

    @Transactional(readOnly = true)
    public List<CourtClosureResponse> getClosures(
            UUID ownerId,
            UUID courtId,
            LocalDate date) {

        requireOwnedCourt(ownerId, courtId);

        return closureRepository
                .findByCourt_IdAndClosureDateOrderByStartTimeAsc(
                        courtId, date)
                .stream()
                .map(CourtClosureResponse::from)
                .toList();
    }

    @Transactional
    public CourtClosureResponse createClosure(
            UUID ownerId,
            UUID courtId,
            CreateCourtClosureRequest request) {

        Court court = requireOwnedCourtForUpdate(ownerId, courtId);

        validateTime(request.startTime(), request.endTime());

        List<CourtClosure> existing = closureRepository
                .findByCourt_IdAndClosureDateOrderByStartTimeAsc(
                        courtId, request.closureDate());

        boolean overlaps = existing.stream()
                .anyMatch(closure ->
                        request.startTime()
                                .isBefore(closure.getEndTime())
                        && request.endTime()
                                .isAfter(closure.getStartTime()));

        if (overlaps) {
            throw new ApiException(
                    CourtErrorCode.COURT_CLOSURE_OVERLAP);
        }

        CourtClosure closure = new CourtClosure();
        closure.setCourt(court);
        closure.setClosureDate(request.closureDate());
        closure.setStartTime(request.startTime());
        closure.setEndTime(request.endTime());
        closure.setReason(request.reason().trim());

        return CourtClosureResponse.from(
                closureRepository.save(closure));
    }

    @Transactional
    public void deleteClosure(
            UUID ownerId,
            UUID courtId,
            UUID closureId) {

        requireOwnedCourtForUpdate(ownerId, courtId);

        CourtClosure closure = closureRepository
                .findByIdAndCourt_Id(closureId, courtId)
                .orElseThrow(() ->
                        new ApiException(
                                CourtErrorCode.COURT_CLOSURE_NOT_FOUND));

        closureRepository.delete(closure);
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

    private void validateTime(
            LocalTime start,
            LocalTime end) {

        if (!isWholeMinute(start)
                || !isWholeMinute(end)
                || !start.isBefore(end)) {

            throw new ApiException(
                    CourtErrorCode.INVALID_CLOSURE_TIME);
        }
    }

    private boolean isWholeMinute(LocalTime time) {
        return time.getSecond() == 0 && time.getNano() == 0;
    }
}
