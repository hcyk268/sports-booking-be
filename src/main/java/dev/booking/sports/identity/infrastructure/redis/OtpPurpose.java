package dev.booking.sports.identity.infrastructure.redis;

public enum OtpPurpose {

	PASSWORD_RESET("reset"),

	PASSWORD_CHANGE("change"),

	ROLE_PERMISSION_CHANGE("role-perm");

	private final String segment;

	OtpPurpose(String segment) {
		this.segment = segment;
	}

	public String segment() {
		return segment;
	}
}
