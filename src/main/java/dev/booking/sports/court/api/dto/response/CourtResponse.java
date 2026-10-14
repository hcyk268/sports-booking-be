package dev.booking.sports.court.api.dto.response;

import java.util.List;
import java.util.UUID;

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
        List<String> amenities,
        String rules) {

    public static CourtResponse from(Court court) {
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
                court.getAmenities().stream().sorted().toList(),
                court.getRules());
    }
}
