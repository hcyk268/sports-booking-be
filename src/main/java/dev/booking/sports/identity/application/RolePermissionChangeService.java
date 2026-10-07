package dev.booking.sports.identity.application;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.booking.sports.identity.api.dto.request.ApplyRolePermissionsRequest;
import dev.booking.sports.identity.api.dto.request.ReplaceRolePermissionsRequest;
import dev.booking.sports.identity.api.dto.response.RolePermissionChangeRequestResponse;
import dev.booking.sports.identity.api.dto.response.RoleResponse;
import dev.booking.sports.identity.api.error.AuthErrorCode;
import dev.booking.sports.identity.domain.event.IdentityOutboxMessageTypes;
import dev.booking.sports.identity.domain.event.RolePermissionChangeOtpIssuedEvent;
import dev.booking.sports.identity.domain.model.Permission;
import dev.booking.sports.identity.domain.model.Role;
import dev.booking.sports.identity.domain.model.User;
import dev.booking.sports.identity.domain.repository.PermissionRepository;
import dev.booking.sports.identity.domain.repository.RoleRepository;
import dev.booking.sports.identity.domain.repository.UserRepository;
import dev.booking.sports.identity.infrastructure.redis.OtpPurpose;
import dev.booking.sports.identity.infrastructure.redis.RolePermissionChangePendingStore;
import dev.booking.sports.identity.infrastructure.security.ApplicationUserDetailsService;
import dev.booking.sports.shared.exception.ApiException;
import dev.booking.sports.shared.outbox.OutboxService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RolePermissionChangeService {

	private final RoleRepository roleRepository;
	private final PermissionRepository permissionRepository;
	private final UserRepository userRepository;
	private final RolePermissionChangePolicy policy;
	private final RolePermissionChangePendingStore pendingStore;
	private final OtpService otpService;
	private final OutboxService outboxService;
	private final AuthSessionService sessionService;
	private final ApplicationUserDetailsService userDetailsService;

	@Transactional(readOnly = true)
	public RolePermissionChangeRequestResponse requestChange(
			UUID actorUserId,
			String roleCode,
			ReplaceRolePermissionsRequest request) {

		String normalizedRoleCode = normalizeRoleCode(roleCode);
		Set<String> permissionCodes = policy.normalizePermissionCodes(request.permissionCodes());
		policy.assertRolePermissionsChangeAllowed(normalizedRoleCode, permissionCodes);
		resolvePermissions(permissionCodes);
		requireRole(normalizedRoleCode);

		User actor = userRepository.findById(actorUserId)
				.orElseThrow(() -> new ApiException(AuthErrorCode.TOKEN_REVOKED));

		pendingStore.save(actorUserId, normalizedRoleCode, permissionCodes, otpService.ttl());

		String otp = otpService.issue(OtpPurpose.ROLE_PERMISSION_CHANGE, actorUserId, normalizedRoleCode);
		outboxService.enqueue(
				IdentityOutboxMessageTypes.USER_AGGREGATE_TYPE,
				actorUserId,
				IdentityOutboxMessageTypes.ROLE_PERMISSION_CHANGE_OTP_ISSUED,
				IdentityOutboxMessageTypes.EVENT_VERSION,
				new RolePermissionChangeOtpIssuedEvent(
						actorUserId,
						actor.getEmail(),
						actor.getFullName(),
						normalizedRoleCode,
						otp,
						otpService.ttl()));

		return new RolePermissionChangeRequestResponse(otpService.ttl().toSeconds());
	}

	@Transactional
	public RoleResponse applyChange(UUID actorUserId, String roleCode, ApplyRolePermissionsRequest request) {
		String normalizedRoleCode = normalizeRoleCode(roleCode);
		Set<String> permissionCodes = policy.normalizePermissionCodes(request.permissionCodes());
		assertPendingMatches(actorUserId, normalizedRoleCode, permissionCodes);
		policy.assertRolePermissionsChangeAllowed(normalizedRoleCode, permissionCodes);

		otpService.verify(OtpPurpose.ROLE_PERMISSION_CHANGE, actorUserId, normalizedRoleCode, request.otp());

		Role role = requireRoleWithPermissions(normalizedRoleCode);
		Set<Permission> permissions = resolvePermissions(permissionCodes);

		role.replacePermissions(permissions);

		pendingStore.delete(actorUserId, normalizedRoleCode);
		revokeSessionsForRole(normalizedRoleCode);

		return RoleResponse.from(requireRoleWithPermissions(normalizedRoleCode));
	}

	private void assertPendingMatches(UUID actorUserId, String roleCode, Set<String> permissionCodes) {
		Set<String> pending = pendingStore.read(actorUserId, roleCode)
				.orElseThrow(() -> new ApiException(AuthErrorCode.ROLE_PERMISSION_CHANGE_PENDING_NOT_FOUND));
		if (!pending.equals(permissionCodes)) {
			throw new ApiException(AuthErrorCode.ROLE_PERMISSION_CHANGE_MISMATCH);
		}
	}

	private Role requireRole(String roleCode) {
		return roleRepository.findByCode(roleCode)
				.orElseThrow(() -> new ApiException(AuthErrorCode.ROLE_NOT_FOUND));
	}

	private Role requireRoleWithPermissions(String roleCode) {
		return roleRepository.findByCodeWithPermissions(roleCode)
				.orElseThrow(() -> new ApiException(AuthErrorCode.ROLE_NOT_FOUND));
	}

	private Set<Permission> resolvePermissions(Set<String> permissionCodes) {
		Set<Permission> permissions = permissionRepository.findByCodeIn(permissionCodes);
		if (permissions.size() != permissionCodes.size()) {
			throw new ApiException(AuthErrorCode.PERMISSION_NOT_FOUND);
		}
		return permissions;
	}

	private void revokeSessionsForRole(String roleCode) {
		for (User user : userRepository.findAllByRoleCode(roleCode)) {
			userDetailsService.evict(user.getEmail());
			sessionService.revokeAllSessions(user.getId());
		}
	}

	private String normalizeRoleCode(String roleCode) {
		if (roleCode == null || roleCode.trim().isEmpty()) {
			throw new ApiException(AuthErrorCode.ROLE_NOT_FOUND);
		}
		return roleCode.trim();
	}
}
