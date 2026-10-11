package dev.booking.sports.shared.exception;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
		Instant timestamp,
		int status,
		String code,
		String message,
		String path,
		Map<String, String> fieldErrors) {
}
