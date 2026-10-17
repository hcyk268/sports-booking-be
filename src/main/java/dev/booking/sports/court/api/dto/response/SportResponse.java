package dev.booking.sports.court.api.dto.response;

import java.util.UUID;

public record SportResponse(
        UUID id,
        String code,
        String name,
        String description) {
}