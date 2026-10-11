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
	ACCESS_DENIED(HttpStatus.FORBIDDEN, "You do not have permission to perform this action"),

	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),
	ROLE_NOT_FOUND(HttpStatus.BAD_REQUEST, "One or more roles do not exist"),
	NO_FIELDS_TO_UPDATE(HttpStatus.BAD_REQUEST, "At least one field must be provided"),
	FULL_NAME_BLANK(HttpStatus.BAD_REQUEST, "Full name cannot be blank"),
	USER_ALREADY_LOCKED(HttpStatus.CONFLICT, "Account is already locked"),
	USER_NOT_LOCKED(HttpStatus.CONFLICT, "Account is not locked"),
	USER_ALREADY_DISABLED(HttpStatus.CONFLICT, "Account is already disabled"),
	CANNOT_MODIFY_SELF(HttpStatus.BAD_REQUEST, "You cannot perform this action on your own account"),

	PERMISSION_NOT_FOUND(HttpStatus.BAD_REQUEST, "One or more permissions do not exist"),
	ROLE_PERMISSION_CHANGE_NOT_ALLOWED(HttpStatus.FORBIDDEN, "Permissions for this role cannot be changed"),
	ADMIN_PERMISSIONS_INSUFFICIENT(HttpStatus.BAD_REQUEST, "ADMIN role must retain all required permissions"),
	ROLE_PERMISSION_CHANGE_PENDING_NOT_FOUND(HttpStatus.BAD_REQUEST, "No pending role permission change was found or it has expired"),
	ROLE_PERMISSION_CHANGE_MISMATCH(HttpStatus.BAD_REQUEST, "Permission codes do not match the pending change request");

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
