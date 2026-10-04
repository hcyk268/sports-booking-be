package dev.booking.sports.shared.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ErrorResponse> handleApiException(
			ApiException exception,
			HttpServletRequest request) {

		ErrorCode errorCode = exception.getErrorCode();

		if (errorCode.status().is5xxServerError()) {
			log.error("Request failed with {}", errorCode.code(), exception);
		}

		ErrorResponse body = new ErrorResponse(
				Instant.now(),
				errorCode.status().value(),
				errorCode.code(),
				exception.getMessage(),
				request.getRequestURI(),
				null);
		return ResponseEntity.status(errorCode.status()).body(body);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
			MethodArgumentNotValidException exception, HttpServletRequest request) {

		Map<String, String> fieldErrors = new LinkedHashMap<>();
		exception.getBindingResult().getFieldErrors()
				.forEach(error -> fieldErrors.putIfAbsent(error.getField(), messageOf(error)));

		ErrorResponse body = new ErrorResponse(
				Instant.now(),
				HttpStatus.BAD_REQUEST.value(),
				"VALIDATION_FAILED",
				"Request validation failed",
				request.getRequestURI(),
				fieldErrors);
		return ResponseEntity.badRequest().body(body);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpectedException(
			Exception exception,
			HttpServletRequest request) {

		log.error("Unhandled exception", exception);

		ErrorResponse body = new ErrorResponse(
				Instant.now(),
				HttpStatus.INTERNAL_SERVER_ERROR.value(),
				"INTERNAL_ERROR",
				"Unexpected error",
				request.getRequestURI(),
				null);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
	}

	private String messageOf(FieldError error) {
		return error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage();
	}
}
