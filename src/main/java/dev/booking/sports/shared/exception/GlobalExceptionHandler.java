package dev.booking.sports.shared.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
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

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ErrorResponse> handleAccessDenied(
			AccessDeniedException exception,
			HttpServletRequest request) {

		ErrorResponse body = new ErrorResponse(
				Instant.now(),
				HttpStatus.FORBIDDEN.value(),
				"ACCESS_DENIED",
				"You do not have permission to perform this action",
				request.getRequestURI(),
				null);
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
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

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ErrorResponse> handleConstraintViolation(
			ConstraintViolationException exception,
			HttpServletRequest request) {

		Map<String, String> fieldErrors = new LinkedHashMap<>();
		exception.getConstraintViolations().forEach(violation -> {
			String property = violation.getPropertyPath() != null ? violation.getPropertyPath().toString() : "param";
			fieldErrors.putIfAbsent(property, violation.getMessage());
		});

		ErrorResponse body = new ErrorResponse(
				Instant.now(),
				HttpStatus.BAD_REQUEST.value(),
				"VALIDATION_FAILED",
				"Request validation failed",
				request.getRequestURI(),
				fieldErrors.isEmpty() ? null : fieldErrors);
		return ResponseEntity.badRequest().body(body);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
			HttpMessageNotReadableException exception,
			HttpServletRequest request) {

		ErrorResponse body = new ErrorResponse(
				Instant.now(),
				HttpStatus.BAD_REQUEST.value(),
				"MALFORMED_JSON",
				"Malformed JSON request or invalid payload format",
				request.getRequestURI(),
				null);
		return ResponseEntity.badRequest().body(body);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleTypeMismatch(
			MethodArgumentTypeMismatchException exception,
			HttpServletRequest request) {

		String requiredType = exception.getRequiredType() != null
				? exception.getRequiredType().getSimpleName()
				: "valid";
		String message = "Parameter '%s' must be of type %s"
				.formatted(exception.getName(), requiredType);

		ErrorResponse body = new ErrorResponse(
				Instant.now(),
				HttpStatus.BAD_REQUEST.value(),
				"INVALID_PARAMETER",
				message,
				request.getRequestURI(),
				null);
		return ResponseEntity.badRequest().body(body);
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ErrorResponse> handleMissingServletRequestParameter(
			MissingServletRequestParameterException exception,
			HttpServletRequest request) {

		ErrorResponse body = new ErrorResponse(
				Instant.now(),
				HttpStatus.BAD_REQUEST.value(),
				"MISSING_PARAMETER",
				"Required parameter '%s' is missing".formatted(exception.getParameterName()),
				request.getRequestURI(),
				null);
		return ResponseEntity.badRequest().body(body);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ErrorResponse> handleMethodNotSupported(
			HttpRequestMethodNotSupportedException exception,
			HttpServletRequest request) {

		ErrorResponse body = new ErrorResponse(
				Instant.now(),
				HttpStatus.METHOD_NOT_ALLOWED.value(),
				"METHOD_NOT_ALLOWED",
				"HTTP method '%s' is not supported for this endpoint".formatted(exception.getMethod()),
				request.getRequestURI(),
				null);
		return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(
			HttpMediaTypeNotSupportedException exception,
			HttpServletRequest request) {

		ErrorResponse body = new ErrorResponse(
				Instant.now(),
				HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
				"UNSUPPORTED_MEDIA_TYPE",
				"Content type '%s' is not supported".formatted(exception.getContentType()),
				request.getRequestURI(),
				null);
		return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(body);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ErrorResponse> handleNoResourceFound(
			NoResourceFoundException exception,
			HttpServletRequest request) {

		ErrorResponse body = new ErrorResponse(
				Instant.now(),
				HttpStatus.NOT_FOUND.value(),
				"RESOURCE_NOT_FOUND",
				"Resource not found: /%s".formatted(exception.getResourcePath()),
				request.getRequestURI(),
				null);
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
			DataIntegrityViolationException exception,
			HttpServletRequest request) {

		log.warn("Database integrity violation at {}: {}", request.getRequestURI(), exception.getMessage());

		String message = exception.getMessage();
		String code = "DATA_CONFLICT";
		String defaultMsg = "A database conflict occurred";

		if (message != null && (message.contains("uq_users_email") || message.contains("users_email_key"))) {
			code = "EMAIL_ALREADY_EXISTS";
			defaultMsg = "Email is already registered";
		}

		ErrorResponse body = new ErrorResponse(
				Instant.now(),
				HttpStatus.CONFLICT.value(),
				code,
				defaultMsg,
				request.getRequestURI(),
				null);
		return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
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
