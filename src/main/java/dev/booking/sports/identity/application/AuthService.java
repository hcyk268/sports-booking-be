package dev.booking.sports.identity.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.booking.sports.identity.api.dto.request.LoginRequest;
import dev.booking.sports.identity.api.dto.request.RegisterRequest;
import dev.booking.sports.identity.api.dto.response.TokenResponse;
import dev.booking.sports.identity.api.dto.response.UserSummaryResponse;
import dev.booking.sports.identity.api.error.AuthErrorCode;
import dev.booking.sports.identity.domain.model.Role;
import dev.booking.sports.identity.domain.model.User;
import dev.booking.sports.identity.domain.repository.RoleRepository;
import dev.booking.sports.identity.domain.repository.UserRepository;
import dev.booking.sports.identity.infrastructure.security.ApplicationUserDetails;
import dev.booking.sports.shared.exception.ApiException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

	private static final String DEFAULT_ROLE_CODE = "CUSTOMER";

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuthenticationManager authenticationManager;
	private final AuthSessionService sessionService;
	private final EmailVerificationService emailVerificationService;

	@Transactional
	public void register(RegisterRequest request) {
		String email = EmailNormalizer.normalize(request.email());

		if (userRepository.existsByEmailIgnoreCase(email)) {
			throw new ApiException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
		}

		Role defaultRole = roleRepository.findByCode(DEFAULT_ROLE_CODE)
				.orElseThrow(() -> new IllegalStateException(
						"Role " + DEFAULT_ROLE_CODE + " is missing"));

		User user = User.register(
				email,
				passwordEncoder.encode(request.password()),
				request.fullName().trim(),
				request.phone());
		user.assignRole(defaultRole);

		userRepository.save(user);
		emailVerificationService.sendVerificationLink(user);
	}

	@Transactional
	public TokenResponse login(LoginRequest request, String userAgent, String ip) {
		String email = EmailNormalizer.normalize(request.email());

		ApplicationUserDetails userDetails = authenticate(email, request.password());
		User user = userRepository.findByIdWithRolesAndPermissions(userDetails.userId())
				.orElseThrow(() -> new ApiException(AuthErrorCode.INVALID_CREDENTIALS));

		requireLoginAllowed(user);

		user.setLastLoginAt(Instant.now());

		AuthSessionService.IssuedTokens tokens = sessionService.startSession(user, userAgent, ip);
		return TokenResponse.of(
				tokens.accessToken(),
				tokens.refreshToken(),
				tokens.expiresInSeconds(),
				UserSummaryResponse.from(user));
	}

	private ApplicationUserDetails authenticate(String email, String password) {
		try {
			Authentication authentication = authenticationManager.authenticate(
					UsernamePasswordAuthenticationToken.unauthenticated(email, password));
			return (ApplicationUserDetails) authentication.getPrincipal();
		}
		catch (LockedException exception) {
			throw new ApiException(AuthErrorCode.ACCOUNT_LOCKED);
		}
		catch (DisabledException exception) {
			throw new ApiException(AuthErrorCode.ACCOUNT_DISABLED);
		}
		catch (AuthenticationException | ClassCastException exception) {
			throw new ApiException(AuthErrorCode.INVALID_CREDENTIALS);
		}
	}

	@Transactional(readOnly = true)
	public TokenResponse refresh(String refreshToken) {
		AuthSessionService.RefreshOutcome outcome = sessionService.validateRefreshToken(refreshToken);

		User user = userRepository.findByIdWithRolesAndPermissions(outcome.userId())
				.orElseThrow(() -> new ApiException(AuthErrorCode.TOKEN_REVOKED));
		requireLoginAllowed(user);

		AuthSessionService.IssuedTokens tokens = sessionService.rotateSession(
				user,
				outcome.jti(),
				outcome.tokenVersion(),
				outcome.refreshHash());

		return TokenResponse.of(
				tokens.accessToken(),
				tokens.refreshToken(),
				tokens.expiresInSeconds(),
				UserSummaryResponse.from(user));
	}

	public void logout(UUID userId, String jti) {
		sessionService.endSession(userId, jti);
	}

	public void logoutAll(UUID userId) {
		sessionService.revokeAllSessions(userId);
	}

	private void requireLoginAllowed(User user) {
		switch (user.getStatus()) {
			case ACTIVE -> {
			}
			case PENDING_VERIFICATION -> throw new ApiException(AuthErrorCode.ACCOUNT_NOT_VERIFIED);
			case LOCKED -> throw new ApiException(AuthErrorCode.ACCOUNT_LOCKED);
			case DISABLED -> throw new ApiException(AuthErrorCode.ACCOUNT_DISABLED);
		}
	}
}
