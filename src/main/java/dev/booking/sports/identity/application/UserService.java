package dev.booking.sports.identity.application;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.booking.sports.identity.api.dto.request.ReplaceUserRolesRequest;
import dev.booking.sports.identity.api.dto.request.UpdateUserProfileRequest;
import dev.booking.sports.identity.api.dto.response.UserSummaryResponse;
import dev.booking.sports.identity.api.error.AuthErrorCode;
import dev.booking.sports.identity.domain.enums.UserStatus;
import dev.booking.sports.identity.domain.model.Role;
import dev.booking.sports.identity.domain.model.User;
import dev.booking.sports.identity.domain.repository.RoleRepository;
import dev.booking.sports.identity.domain.repository.UserRepository;
import dev.booking.sports.identity.infrastructure.security.ApplicationUserDetailsService;
import dev.booking.sports.shared.api.PageResponse;
import dev.booking.sports.shared.exception.ApiException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

	private static final int MAX_PAGE_SIZE = 100;

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final ApplicationUserDetailsService userDetailsService;
	private final AuthSessionService sessionService;

	@Transactional(readOnly = true)
	public UserSummaryResponse getMe(UUID userId) {
		return UserSummaryResponse.from(requireUser(userId));
	}

	@Transactional
	public UserSummaryResponse updateMe(UUID userId, UpdateUserProfileRequest request) {
		User user = requireUser(userId);
		applyProfilePatch(user, request);
		userDetailsService.evict(user.getEmail());
		return UserSummaryResponse.from(user);
	}

	@Transactional(readOnly = true)
	public PageResponse<UserSummaryResponse> listUsers(UserStatus status, int page, int size) {
		Pageable pageable = PageRequest.of(page, clampSize(size), Sort.by(Sort.Direction.DESC, "createdAt"));
		Page<User> users = userRepository.findAllPaged(status, pageable);
		List<UserSummaryResponse> items = users.getContent().stream()
				.map(UserSummaryResponse::from)
				.toList();
		return PageResponse.of(items, users.getNumber(), users.getSize(), users.getTotalElements());
	}

	@Transactional(readOnly = true)
	public UserSummaryResponse getById(UUID id) {
		return UserSummaryResponse.from(requireUser(id));
	}

	@Transactional
	public UserSummaryResponse updateById(UUID id, UpdateUserProfileRequest request) {
		User user = requireUser(id);
		applyProfilePatch(user, request);
		userDetailsService.evict(user.getEmail());
		return UserSummaryResponse.from(user);
	}

	@Transactional
	public UserSummaryResponse replaceRoles(UUID actorId, UUID id, ReplaceUserRolesRequest request) {
		assertNotSelf(actorId, id);
		User user = requireUser(id);
		Set<Role> roles = resolveRoles(request.roleCodes());
		user.replaceRoles(roles);
		userDetailsService.evict(user.getEmail());
		sessionService.revokeAllSessions(id);
		return UserSummaryResponse.from(user);
	}

	@Transactional
	public UserSummaryResponse lock(UUID actorId, UUID id) {
		assertNotSelf(actorId, id);
		User user = requireUser(id);
		if (user.getStatus() == UserStatus.LOCKED) {
			throw new ApiException(AuthErrorCode.USER_ALREADY_LOCKED);
		}
		if (user.getStatus() == UserStatus.DISABLED) {
			throw new ApiException(AuthErrorCode.USER_ALREADY_DISABLED);
		}
		user.lock();
		userDetailsService.evict(user.getEmail());
		sessionService.revokeAllSessions(id);
		return UserSummaryResponse.from(user);
	}

	@Transactional
	public UserSummaryResponse unlock(UUID id) {
		User user = requireUser(id);
		if (user.getStatus() != UserStatus.LOCKED) {
			throw new ApiException(AuthErrorCode.USER_NOT_LOCKED);
		}
		user.unlock();
		userDetailsService.evict(user.getEmail());
		return UserSummaryResponse.from(user);
	}

	@Transactional
	public UserSummaryResponse disable(UUID actorId, UUID id) {
		assertNotSelf(actorId, id);
		User user = requireUser(id);
		if (user.getStatus() == UserStatus.DISABLED) {
			throw new ApiException(AuthErrorCode.USER_ALREADY_DISABLED);
		}
		user.disable();
		userDetailsService.evict(user.getEmail());
		sessionService.revokeAllSessions(id);
		return UserSummaryResponse.from(user);
	}

	private User requireUser(UUID id) {
		return userRepository.findByIdWithRolesAndPermissions(id)
				.orElseThrow(() -> new ApiException(AuthErrorCode.USER_NOT_FOUND));
	}

	private void applyProfilePatch(User user, UpdateUserProfileRequest request) {
		boolean hasFullName = request.fullName() != null;
		boolean hasPhone = request.phone() != null;
		if (!hasFullName && !hasPhone) {
			throw new ApiException(AuthErrorCode.NO_FIELDS_TO_UPDATE);
		}
		if (hasFullName) {
			String fullName = request.fullName().trim();
			if (fullName.isEmpty()) {
				throw new ApiException(AuthErrorCode.NO_FIELDS_TO_UPDATE);
			}
			user.setFullName(fullName);
		}
		if (hasPhone) {
			user.setPhone(request.phone());
		}
	}

	private Set<Role> resolveRoles(Set<String> roleCodes) {
		Set<String> normalizedCodes = roleCodes.stream()
				.map(String::trim)
				.filter(code -> !code.isEmpty())
				.collect(Collectors.toCollection(LinkedHashSet::new));
		if (normalizedCodes.isEmpty()) {
			throw new ApiException(AuthErrorCode.ROLE_NOT_FOUND);
		}

		Set<Role> roles = new LinkedHashSet<>();
		for (String code : normalizedCodes) {
			Role role = roleRepository.findByCode(code)
					.orElseThrow(() -> new ApiException(AuthErrorCode.ROLE_NOT_FOUND));
			roles.add(role);
		}
		return roles;
	}

	private void assertNotSelf(UUID actorId, UUID targetId) {
		if (actorId.equals(targetId)) {
			throw new ApiException(AuthErrorCode.CANNOT_MODIFY_SELF);
		}
	}

	private int clampSize(int size) {
		if (size < 1) {
			return 20;
		}
		return Math.min(size, MAX_PAGE_SIZE);
	}
}
