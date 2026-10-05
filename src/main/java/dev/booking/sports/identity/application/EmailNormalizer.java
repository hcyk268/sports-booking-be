package dev.booking.sports.identity.application;

import java.util.Locale;

final class EmailNormalizer {

	private EmailNormalizer() {
	}

	static String normalize(String email) {
		return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
	}
}
