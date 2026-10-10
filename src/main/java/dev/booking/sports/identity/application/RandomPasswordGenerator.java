package dev.booking.sports.identity.application;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class RandomPasswordGenerator {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final String LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz";
	private static final String DIGITS = "23456789";
	private static final String ALL = LETTERS + DIGITS;
	private static final int DEFAULT_LENGTH = 14;

	private RandomPasswordGenerator() {
	}

	static String generate() {
		List<Character> chars = new ArrayList<>(DEFAULT_LENGTH);
		chars.add(randomChar(LETTERS));
		chars.add(randomChar(DIGITS));
		while (chars.size() < DEFAULT_LENGTH) {
			chars.add(randomChar(ALL));
		}
		Collections.shuffle(chars, RANDOM);
		StringBuilder password = new StringBuilder(DEFAULT_LENGTH);
		for (char value : chars) {
			password.append(value);
		}
		return password.toString();
	}

	private static char randomChar(String alphabet) {
		return alphabet.charAt(RANDOM.nextInt(alphabet.length()));
	}
}
