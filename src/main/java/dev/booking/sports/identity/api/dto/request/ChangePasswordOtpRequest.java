package dev.booking.sports.identity.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordOtpRequest(@NotBlank String currentPassword) {
}
