package dev.booking.sports.court.api.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateCourtRequest(
        @NotNull @PositiveOrZero Long version,
        @NotNull UUID sportId,
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 255) String address,
        @NotBlank @Size(max = 100) String city,
        @Size(max = 100) String ward,
        String description,
        @Size(max = 30)
        List<@NotBlank @Size(max = 100) String> amenities,
        @Size(max = 5000) String rules) {
}
