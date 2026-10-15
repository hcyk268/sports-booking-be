package dev.booking.sports.court.application;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.booking.sports.court.api.dto.request.CreateCourtRequest;
import dev.booking.sports.court.api.dto.request.UpdateCourtRequest;
import dev.booking.sports.court.api.dto.response.CourtResponse;
import dev.booking.sports.court.api.error.CourtErrorCode;
import dev.booking.sports.court.domain.enums.CourtStatus;
import dev.booking.sports.court.domain.model.Court;
import dev.booking.sports.court.domain.model.Sport;
import dev.booking.sports.court.domain.repository.CourtRepository;
import dev.booking.sports.court.domain.repository.SportRepository;
import dev.booking.sports.shared.api.PageResponse;
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
        applyAmenitiesAndRules(court, request.amenities(), request.rules());
        court.setStatus(CourtStatus.DRAFT);

        return CourtResponse.forOwner(courtRepository.save(court));
    }

    @Transactional
    public CourtResponse updateCourt(
            UUID ownerId,
            UUID courtId,
            UpdateCourtRequest request) {

        Court court = courtRepository.findById(courtId)
                .orElseThrow(() ->
                        new ApiException(CourtErrorCode.COURT_NOT_FOUND));

        if (!court.getOwnerId().equals(ownerId)) {
            throw new ApiException(CourtErrorCode.COURT_ACCESS_DENIED);
        }

        Sport sport = sportRepository.findById(request.sportId())
                .orElseThrow(() ->
                        new ApiException(CourtErrorCode.SPORT_NOT_FOUND));

        court.setSport(sport);
        court.setName(request.name().trim());
        court.setAddress(request.address().trim());
        court.setCity(request.city().trim());
        court.setWard(request.ward() == null
                ? null : request.ward().trim());
        court.setDescription(request.description() == null
                ? null : request.description().trim());
        applyAmenitiesAndRules(court, request.amenities(), request.rules());

        return CourtResponse.forOwner(courtRepository.save(court));
    }

    @Transactional(readOnly = true)
    public PageResponse<CourtResponse> searchPublicCourts(
            String keyword,
            String city,
            UUID sportId,
            int page,
            int size) {

        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(CourtErrorCode.INVALID_PAGINATION);
        }

        Specification<Court> specification =
                CourtSpecifications.publicCourts(keyword, city, sportId);

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by("name", "id"));

        Page<Court> result = courtRepository.findAll(
                specification,
                pageable);

        List<CourtResponse> items = result.getContent()
                .stream()
                .map(CourtResponse::from)
                .toList();

        return PageResponse.of(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public CourtResponse getPublicCourt(UUID courtId) {
        Court court = courtRepository
                .findByIdAndStatus(courtId, CourtStatus.ACTIVE)
                .orElseThrow(() ->
                        new ApiException(CourtErrorCode.COURT_NOT_FOUND));

        return CourtResponse.from(court);
    }

    @Transactional
    public CourtResponse publishCourt(UUID ownerId, UUID courtId) {

        Court court = courtRepository.findById(courtId)
                .orElseThrow(() ->
                        new ApiException(CourtErrorCode.COURT_NOT_FOUND));

        if (!court.getOwnerId().equals(ownerId)) {
            throw new ApiException(CourtErrorCode.COURT_ACCESS_DENIED);
        }

        if (court.getStatus() == CourtStatus.ACTIVE) {
            return CourtResponse.forOwner(court);
        }

        if (court.getStatus() != CourtStatus.DRAFT) {
            throw new ApiException(
                    CourtErrorCode.COURT_PUBLISH_NOT_ALLOWED);
        }

        court.setStatus(CourtStatus.ACTIVE);
        return CourtResponse.forOwner(courtRepository.save(court));
    }

    private void applyAmenitiesAndRules(
            Court court,
            List<String> amenities,
            String rules) {

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (amenities != null) {
            amenities.stream()
                    .map(String::trim)
                    .forEach(normalized::add);
        }

        court.getAmenities().clear();
        court.getAmenities().addAll(normalized);
        court.setRules(rules == null || rules.isBlank()
                ? null : rules.trim());
    }

}
