package dev.booking.sports.identity.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import dev.booking.sports.identity.api.dto.request.LoginRequest;
import dev.booking.sports.identity.api.dto.request.RegisterRequest;
import dev.booking.sports.identity.api.error.AuthErrorCode;
import dev.booking.sports.identity.domain.enums.UserStatus;
import dev.booking.sports.identity.domain.model.Role;
import dev.booking.sports.identity.domain.model.User;
import dev.booking.sports.identity.domain.repository.RoleRepository;
import dev.booking.sports.identity.domain.repository.UserRepository;
import dev.booking.sports.identity.infrastructure.security.ApplicationUserDetails;
import dev.booking.sports.shared.exception.ApiException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	private static final UUID USER_ID = UUID.fromString("36363636-3636-3636-3636-363636363636");

	@Mock
	private UserRepository userRepository;

	@Mock
	private RoleRepository roleRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private AuthenticationManager authenticationManager;

	@Mock
	private AuthSessionService sessionService;

	@Mock
	private EmailVerificationService emailVerificationService;

	@InjectMocks
	private AuthService authService;

	@Test
	void register_duplicateEmail_throws() {
		when(userRepository.existsByEmailIgnoreCase("user@example.com")).thenReturn(true);

		RegisterRequest request = new RegisterRequest("  User@Example.COM  ", "Password1", "Test User", null);

		assertThatThrownBy(() -> authService.register(request))
				.isInstanceOf(ApiException.class)
				.extracting(exception -> ((ApiException) exception).getErrorCode())
				.isEqualTo(AuthErrorCode.EMAIL_ALREADY_EXISTS);

		verify(userRepository, never()).save(any());
	}

	@Test
	void register_success_savesUserAndSendsVerification() {
		when(userRepository.existsByEmailIgnoreCase("user@example.com")).thenReturn(false);
		Role customerRole = new Role();
		customerRole.setCode("CUSTOMER");
		when(roleRepository.findByCode("CUSTOMER")).thenReturn(Optional.of(customerRole));
		when(passwordEncoder.encode("Password1")).thenReturn("encoded-password");

		RegisterRequest request = new RegisterRequest("user@example.com", "Password1", "  Test User  ", "0901");

		authService.register(request);

		verify(userRepository).save(any(User.class));
		verify(emailVerificationService).sendVerificationLink(any(User.class));
	}

	@Test
	void login_pendingVerification_throwsAccountNotVerified() {
		ApplicationUserDetails principal = new ApplicationUserDetails(
				USER_ID,
				"user@example.com",
				"hash",
				UserStatus.ACTIVE,
				Set.of("CUSTOMER"),
				Set.of());
		when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
				.thenReturn(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

		User pendingUser = User.register("user@example.com", "hash", "Test User", null);
		pendingUser.setId(USER_ID);
		when(userRepository.findByIdWithRolesAndPermissions(USER_ID)).thenReturn(Optional.of(pendingUser));

		LoginRequest request = new LoginRequest("user@example.com", "Password1");

		assertThatThrownBy(() -> authService.login(request, "JUnit", "127.0.0.1"))
				.isInstanceOf(ApiException.class)
				.extracting(exception -> ((ApiException) exception).getErrorCode())
				.isEqualTo(AuthErrorCode.ACCOUNT_NOT_VERIFIED);

		verify(sessionService, never()).startSession(any(), any(), any());
	}
}
