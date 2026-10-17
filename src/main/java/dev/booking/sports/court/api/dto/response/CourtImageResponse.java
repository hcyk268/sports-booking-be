package dev.booking.sports.court.api.dto.response;

import java.util.UUID;
import dev.booking.sports.court.domain.model.CourtImage;

public record CourtImageResponse(
        UUID id,
        String url,
        boolean cover,
        int displayOrder) {

    public static CourtImageResponse from(CourtImage image) {
        return from(image, "/api/v1/courts/");
    }

    public static CourtImageResponse forOwner(CourtImage image) {
        return from(image, "/api/v1/provider/courts/");
    }

    private static CourtImageResponse from(CourtImage image, String prefix) {
        return new CourtImageResponse(
                image.getId(),
                prefix + image.getCourt().getId()
                        + "/images/" + image.getId() + "/content",
                image.isCover(),
                image.getDisplayOrder());
    }
}
