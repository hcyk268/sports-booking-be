package dev.booking.sports.identity.api.dto.request;

final class PasswordRules {

	static final String PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).{8,36}$";

	static final String MESSAGE = "must be 8-36 characters and contain at least one letter and one digit";

	private PasswordRules() {
	}
}
