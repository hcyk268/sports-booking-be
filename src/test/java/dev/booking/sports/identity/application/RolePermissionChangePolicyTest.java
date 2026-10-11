package dev.booking.sports.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.booking.sports.identity.api.error.AuthErrorCode;
import dev.booking.sports.shared.exception.ApiException;

class RolePermissionChangePolicyTest {

	private final RolePermissionChangePolicy policy = new RolePermissionChangePolicy();

	@Test
	void assertRolePermissionsChangeAllowed_rejectsCustomerRole() {
		assertThatThrownBy(() -> policy.assertRolePermissionsChangeAllowed("CUSTOMER", Set.of("USER_READ_SELF")))
				.isInstanceOf(ApiException.class)
				.extracting(exception -> ((ApiException) exception).getErrorCode())
				.isEqualTo(AuthErrorCode.ROLE_PERMISSION_CHANGE_NOT_ALLOWED);
	}

	@Test
	void assertRolePermissionsChangeAllowed_requiresAdminFloor() {
		assertThatThrownBy(() -> policy.assertRolePermissionsChangeAllowed("ADMIN", Set.of("USER_READ")))
				.isInstanceOf(ApiException.class)
				.extracting(exception -> ((ApiException) exception).getErrorCode())
				.isEqualTo(AuthErrorCode.ADMIN_PERMISSIONS_INSUFFICIENT);
	}

	@Test
	void normalizePermissionCodes_sortsAndTrims() {
		Set<String> normalized = policy.normalizePermissionCodes(Set.of(" USER_READ_SELF ", "USER_UPDATE_SELF"));

		assertThat(normalized).containsExactlyInAnyOrder("USER_READ_SELF", "USER_UPDATE_SELF");
	}
}
