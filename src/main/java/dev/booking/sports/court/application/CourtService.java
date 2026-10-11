package dev.booking.sports.court.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.booking.sports.court.api.dto.request.CreateCourtRequest;
import dev.booking.sports.court.api.dto.response.CourtResponse;
import dev.booking.sports.court.api.error.CourtErrorCode;
import dev.booking.sports.court.domain.enums.CourtStatus;
import dev.booking.sports.court.domain.model.Court;
import dev.booking.sports.court.domain.model.Sport;
import dev.booking.sports.court.domain.repository.CourtRepository;
import dev.booking.sports.court.domain.repository.SportRepository;
import dev.booking.sports.shared.exception.ApiException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CourtService {

    private final CourtRepository courtRepository;
    private final SportRepository sportRepository;

    @Transactional
    public CourtResponse createCourt(
            UUID ownerId,
            CreateCourtRequest request) {

        Sport sport = sportRepository.findById(request.sportId())
                .orElseThrow(() ->
                        new ApiException(CourtErrorCode.SPORT_NOT_FOUND));

        Court court = new Court();
        court.setOwnerId(ownerId);
        court.setSport(sport);
        court.setName(request.name().trim());
        court.setAddress(request.address().trim());
        court.setCity(request.city().trim());
        court.setWard(request.ward() == null
                ? null : request.ward().trim());
        court.setDescription(request.description() == null
                ? null : request.description().trim());
        court.setStatus(CourtStatus.DRAFT);

        Court savedCourt = courtRepository.save(court);
        return CourtResponse.from(savedCourt);
    }
}