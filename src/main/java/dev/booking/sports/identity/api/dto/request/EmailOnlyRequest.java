package dev.booking.sports.identity.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailOnlyRequest(@NotBlank @Email @Size(max = 255) String email) {
}
