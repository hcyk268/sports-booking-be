package dev.booking.sports.identity.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.booking.sports.identity.api.dto.response.PermissionResponse;
import dev.booking.sports.identity.api.dto.response.RoleResponse;
import dev.booking.sports.identity.api.error.AuthErrorCode;
import dev.booking.sports.identity.domain.repository.PermissionRepository;
import dev.booking.sports.identity.domain.repository.RoleRepository;
import dev.booking.sports.shared.exception.ApiException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RbacService {

	private final PermissionRepository permissionRepository;
	private final RoleRepository roleRepository;

	@Transactional(readOnly = true)
	public List<PermissionResponse> listPermissions(String module) {
		String normalizedModule = normalizeModuleFilter(module);
		return permissionRepository.findAllOrdered(normalizedModule).stream()
				.map(PermissionResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<RoleResponse> listRoles() {
		return roleRepository.findAllWithPermissions().stream()
				.map(RoleResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public RoleResponse getRoleByCode(String code) {
		String normalizedCode = code == null ? "" : code.trim();
		if (normalizedCode.isEmpty()) {
			throw new ApiException(AuthErrorCode.ROLE_NOT_FOUND);
		}
		return roleRepository.findByCodeWithPermissions(normalizedCode)
				.map(RoleResponse::from)
				.orElseThrow(() -> new ApiException(AuthErrorCode.ROLE_NOT_FOUND));
	}

	private String normalizeModuleFilter(String module) {
		if (module == null) {
			return null;
		}
		String trimmed = module.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
