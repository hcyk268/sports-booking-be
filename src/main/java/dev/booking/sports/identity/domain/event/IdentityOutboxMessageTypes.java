package dev.booking.sports.identity.domain.event;

public final class IdentityOutboxMessageTypes {

	public static final String USER_AGGREGATE_TYPE = "USER";
	public static final int EVENT_VERSION = 1;

	public static final String EMAIL_VERIFICATION_REQUESTED = "identity.email-verification-requested";
	public static final String PASSWORD_RESET_OTP_ISSUED = "identity.password-reset-otp-issued";
	public static final String PASSWORD_CHANGE_OTP_ISSUED = "identity.password-change-otp-issued";
	public static final String PASSWORD_CHANGED = "identity.password-changed";

	private IdentityOutboxMessageTypes() {
	}
}
