package dev.booking.sports.court.api.dto.response;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import dev.booking.sports.court.domain.enums.CourtStatus;
import dev.booking.sports.court.domain.model.Court;

public record CourtResponse(
        UUID id,
        UUID ownerId,
        UUID sportId,
        String sportName,
        String name,
        String address,
        String city,
        String ward,
        String description,
        CourtStatus status,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Long version,
        List<String> amenities,
        String rules,
        List<CourtImageResponse> images,
        String coverImageUrl) {

    public static CourtResponse from(Court court) {
        return from(court, false);
    }

    public static CourtResponse forOwner(Court court) {
        return from(court, true);
    }

    private static CourtResponse from(Court court, boolean ownerView) {
        List<CourtImageResponse> gallery = court.getImages().stream()
                .map(image -> ownerView
                        ? CourtImageResponse.forOwner(image)
                        : CourtImageResponse.from(image))
                .toList();
        return new CourtResponse(
                court.getId(),
                court.getOwnerId(),
                court.getSport().getId(),
                court.getSport().getName(),
                court.getName(),
                court.getAddress(),
                court.getCity(),
                court.getWard(),
                court.getDescription(),
                court.getStatus(),
                ownerView ? court.getVersion() : null,
                court.getAmenities().stream().sorted().toList(),
                court.getRules(),
                gallery,
                gallery.stream()
                        .filter(CourtImageResponse::cover)
                        .findFirst()
                        .map(CourtImageResponse::url)
                        .orElse(null));
    }
}
