package dev.booking.sports.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import dev.booking.sports.identity.domain.enums.UserStatus;

class UserTest {

	@Test
	void register_setsPendingVerification() {
		User user = User.register("user@example.com", "hash", "Test User", "0901234567");

		assertThat(user.getEmail()).isEqualTo("user@example.com");
		assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
		assertThat(user.getEmailVerifiedAt()).isNull();
	}

	@Test
	void activate_setsActiveAndVerifiedAt() {
		User user = User.register("user@example.com", "hash", "Test User", null);
		Instant verifiedAt = Instant.parse("2026-01-01T00:00:00Z");

		user.activate(verifiedAt);

		assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(user.getEmailVerifiedAt()).isEqualTo(verifiedAt);
	}

	@Test
	void provisionedByAdmin_setsActiveAndVerified() {
		Instant verifiedAt = Instant.parse("2026-01-01T00:00:00Z");
		User user = User.createByAdmin("staff@example.com", "hash", "Staff User", "0901111111", verifiedAt);

		assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(user.getEmailVerifiedAt()).isEqualTo(verifiedAt);
	}

	@Test
	void assignRole_exposesRoleCode() {
		User user = User.register("user@example.com", "hash", "Test User", null);
		Role role = new Role();
		role.setCode("CUSTOMER");
		role.setName("Customer");

		user.assignRole(role);

		assertThat(user.roleCodes()).containsExactly("CUSTOMER");
	}
}
