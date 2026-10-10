package dev.booking.sports.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

class GlobalExceptionHandlerTest {

	private GlobalExceptionHandler handler;
	private MockHttpServletRequest request;

	@BeforeEach
	void setUp() {
		handler = new GlobalExceptionHandler();
		request = new MockHttpServletRequest();
		request.setRequestURI("/api/v1/test");
	}

	@Test
	void handleAccessDenied_returns403() {
		AccessDeniedException ex = new AccessDeniedException("Forbidden");

		ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(ex, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().status()).isEqualTo(403);
		assertThat(response.getBody().code()).isEqualTo("ACCESS_DENIED");
	}

	@Test
	void handleDataIntegrityViolation_duplicateEmail_returns409EmailAlreadyExists() {
		DataIntegrityViolationException ex = new DataIntegrityViolationException(
				"ERROR: duplicate key value violates unique constraint \"uq_users_email\"");

		ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().status()).isEqualTo(409);
		assertThat(response.getBody().code()).isEqualTo("EMAIL_ALREADY_EXISTS");
	}

	@Test
	void handleDataIntegrityViolation_otherConflict_returns409DataConflict() {
		DataIntegrityViolationException ex = new DataIntegrityViolationException("foreign key violation");

		ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().status()).isEqualTo(409);
		assertThat(response.getBody().code()).isEqualTo("DATA_CONFLICT");
	}

	@Test
	void handleHttpMessageNotReadable_returns400() {
		HttpMessageNotReadableException ex = new HttpMessageNotReadableException("JSON parse error", (org.springframework.http.HttpInputMessage) null);

		ResponseEntity<ErrorResponse> response = handler.handleHttpMessageNotReadable(ex, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("MALFORMED_JSON");
	}

	@Test
	void handleTypeMismatch_returns400() {
		MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
		when(ex.getName()).thenReturn("id");
		when(ex.getRequiredType()).thenAnswer(inv -> java.util.UUID.class);

		ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(ex, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("INVALID_PARAMETER");
		assertThat(response.getBody().message()).contains("id").contains("UUID");
	}

	@Test
	void handleMissingServletRequestParameter_returns400() {
		MissingServletRequestParameterException ex = new MissingServletRequestParameterException("token", "String");

		ResponseEntity<ErrorResponse> response = handler.handleMissingServletRequestParameter(ex, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("MISSING_PARAMETER");
		assertThat(response.getBody().message()).contains("token");
	}

	@Test
	void handleConstraintViolation_returns400WithFieldErrors() {
		@SuppressWarnings("unchecked")
		ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
		Path path = mock(Path.class);
		when(path.toString()).thenReturn("page");
		when(violation.getPropertyPath()).thenReturn(path);
		when(violation.getMessage()).thenReturn("must be greater than or equal to 0");

		ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation));

		ResponseEntity<ErrorResponse> response = handler.handleConstraintViolation(ex, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("VALIDATION_FAILED");
		assertThat(response.getBody().fieldErrors()).containsEntry("page", "must be greater than or equal to 0");
	}

	@Test
	void handleMethodNotSupported_returns405() {
		HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("POST");

		ResponseEntity<ErrorResponse> response = handler.handleMethodNotSupported(ex, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("METHOD_NOT_ALLOWED");
	}

	@Test
	void handleMediaTypeNotSupported_returns415() {
		HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN_VALUE);

		ResponseEntity<ErrorResponse> response = handler.handleMediaTypeNotSupported(ex, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
	}

	@Test
	void handleNoResourceFound_returns404() {
		NoResourceFoundException ex = mock(NoResourceFoundException.class);
		when(ex.getResourcePath()).thenReturn("api/v1/unknown");

		ResponseEntity<ErrorResponse> response = handler.handleNoResourceFound(ex, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("RESOURCE_NOT_FOUND");
	}

	@Test
	void handleUnexpectedException_returns500() {
		RuntimeException ex = new RuntimeException("Something exploded");

		ResponseEntity<ErrorResponse> response = handler.handleUnexpectedException(ex, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
	}
}
