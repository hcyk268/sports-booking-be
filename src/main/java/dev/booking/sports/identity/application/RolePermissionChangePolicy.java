package dev.booking.sports.identity.application;

import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Component;

import dev.booking.sports.identity.api.error.AuthErrorCode;
import dev.booking.sports.shared.exception.ApiException;

@Component
public class RolePermissionChangePolicy {

	private static final String CUSTOMER_ROLE_CODE = "CUSTOMER";
	private static final String ADMIN_ROLE_CODE = "ADMIN";

	private static final Set<String> ADMIN_REQUIRED_PERMISSIONS = Set.of(
			"USER_READ",
			"USER_CREATE",
			"USER_UPDATE",
			"USER_DELETE",
			"USER_MANAGE_ROLE",
			"USER_LOCK",
			"USER_UNLOCK",
			"ROLE_READ",
			"ROLE_MANAGE");

	public void assertRolePermissionsChangeAllowed(String roleCode, Set<String> permissionCodes) {
		if (CUSTOMER_ROLE_CODE.equals(roleCode)) {
			throw new ApiException(AuthErrorCode.ROLE_PERMISSION_CHANGE_NOT_ALLOWED);
		}
		if (ADMIN_ROLE_CODE.equals(roleCode) && !permissionCodes.containsAll(ADMIN_REQUIRED_PERMISSIONS)) {
			throw new ApiException(AuthErrorCode.ADMIN_PERMISSIONS_INSUFFICIENT);
		}
	}

	public Set<String> normalizePermissionCodes(Set<String> permissionCodes) {
		if (permissionCodes == null || permissionCodes.isEmpty()) {
			throw new ApiException(AuthErrorCode.PERMISSION_NOT_FOUND);
		}
		Set<String> normalized = permissionCodes.stream()
				.map(String::trim)
				.filter(code -> !code.isEmpty())
				.collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
		if (normalized.isEmpty()) {
			throw new ApiException(AuthErrorCode.PERMISSION_NOT_FOUND);
		}
		return Set.copyOf(normalized);
	}
}
