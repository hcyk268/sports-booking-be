package dev.booking.sports.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailNormalizerTest {

	@Test
	void normalize_trimsAndLowerCases() {
		assertThat(EmailNormalizer.normalize("  User@Example.COM  ")).isEqualTo("user@example.com");
	}

	@Test
	void normalize_null_returnsNull() {
		assertThat(EmailNormalizer.normalize(null)).isNull();
	}
}
