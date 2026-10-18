package dev.booking.sports.shared.upload;

import org.springframework.http.HttpStatus;
import dev.booking.sports.shared.exception.ErrorCode;

public enum UploadErrorCode implements ErrorCode {
    UPLOAD_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Object storage is not configured or is unavailable"),
    UPLOAD_NOT_FOUND(HttpStatus.NOT_FOUND, "Upload does not exist"),
    UPLOAD_INVALID(HttpStatus.BAD_REQUEST, "Invalid image or upload metadata"),
    UPLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Image exceeds the permitted size"),
    UPLOAD_EXPIRED(HttpStatus.CONFLICT, "Upload expired; start a new upload"),
    UPLOAD_NOT_READY(HttpStatus.CONFLICT, "Upload has not been verified or is already attached"),
    UPLOAD_CHANGED(HttpStatus.CONFLICT, "Object changed during verification; retry completion"),
    UPLOAD_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "Too many unfinished uploads; retry later");

    private final HttpStatus status;
    private final String message;
    UploadErrorCode(HttpStatus status, String message) { this.status = status; this.message = message; }
    public String code() { return name(); }
    public HttpStatus status() { return status; }
    public String defaultMessage() { return message; }
}
