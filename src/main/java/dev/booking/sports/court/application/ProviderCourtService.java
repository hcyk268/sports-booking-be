package dev.booking.sports.court.application;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.booking.sports.court.api.dto.response.CourtResponse;
import dev.booking.sports.court.api.error.CourtErrorCode;
import dev.booking.sports.court.domain.model.Court;
import dev.booking.sports.court.domain.repository.CourtRepository;
import dev.booking.sports.shared.api.PageResponse;
import dev.booking.sports.shared.exception.ApiException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProviderCourtService {

    private final CourtRepository courtRepository;

    @Transactional(readOnly = true)
    public PageResponse<CourtResponse> getMyCourts(
            UUID ownerId,
            int page,
            int size) {

        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(
                    CourtErrorCode.INVALID_PAGINATION);
        }

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by("name", "id"));

        Page<Court> result = courtRepository.findByOwnerId(
                ownerId,
                pageable);

        List<CourtResponse> items = result.getContent()
                .stream()
                .map(CourtResponse::forOwner)
                .toList();

        return PageResponse.of(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public CourtResponse getMyCourt(
            UUID ownerId,
            UUID courtId) {

        Court court = courtRepository
                .findByIdAndOwnerId(courtId, ownerId)
                .orElseThrow(() ->
                        new ApiException(
                                CourtErrorCode.COURT_NOT_FOUND));

        return CourtResponse.forOwner(court);
    }
}
