package dev.booking.sports.identity.api.error;

import org.springframework.http.HttpStatus;

import dev.booking.sports.shared.exception.ErrorCode;

public enum AuthErrorCode implements ErrorCode {

	EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email is already registered"),
	VERIFICATION_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "Verification link is invalid or has expired"),
	ACCOUNT_ALREADY_VERIFIED(HttpStatus.CONFLICT, "Account has already been verified"),

	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email or password is incorrect"),
	ACCOUNT_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Account has not been activated yet"),
	ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "Account is locked"),
	ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "Account is disabled"),

	TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Token is invalid or has expired"),
	TOKEN_REVOKED(HttpStatus.UNAUTHORIZED, "Token has been revoked, please sign in again"),
	REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "Refresh token was already used, all sessions were revoked"),

	OTP_INVALID(HttpStatus.BAD_REQUEST, "Verification code is incorrect"),
	OTP_NOT_FOUND(HttpStatus.BAD_REQUEST, "Verification code is missing or has expired"),
	OTP_TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "Too many incorrect attempts, request a new code"),

	INVALID_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "Current password is incorrect"),
	PASSWORD_REUSED(HttpStatus.BAD_REQUEST, "New password must differ from the current one"),

	AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "Authentication is required"),
	ACCESS_DENIED(HttpStatus.FORBIDDEN, "You do not have permission to perform this action");

	private final HttpStatus status;
	private final String defaultMessage;

	AuthErrorCode(HttpStatus status, String defaultMessage) {
		this.status = status;
		this.defaultMessage = defaultMessage;
	}

	@Override
	public String code() {
		return name();
	}

	@Override
	public HttpStatus status() {
		return status;
	}

	@Override
	public String defaultMessage() {
		return defaultMessage;
	}
}
