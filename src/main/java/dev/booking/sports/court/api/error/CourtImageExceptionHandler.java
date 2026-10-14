package dev.booking.sports.court.api.error;

import java.time.Instant;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import dev.booking.sports.shared.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CourtImageExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> tooLarge(
            MaxUploadSizeExceededException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
                new ErrorResponse(Instant.now(), 413, "COURT_IMAGE_TOO_LARGE",
                        "Image must not exceed 5 MiB", request.getRequestURI(), null));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> missingFile(
            MissingServletRequestPartException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(
                new ErrorResponse(Instant.now(), 400, "MISSING_UPLOAD_PART",
                        "Required upload part is missing: " + exception.getRequestPartName(),
                        request.getRequestURI(), null));
    }
}
