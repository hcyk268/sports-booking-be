package dev.booking.sports.identity.api.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.booking.sports.identity.api.dto.request.ChangePasswordOtpRequest;
import dev.booking.sports.identity.api.dto.request.ChangePasswordRequest;
import dev.booking.sports.identity.api.dto.request.EmailOnlyRequest;
import dev.booking.sports.identity.api.dto.request.LoginRequest;
import dev.booking.sports.identity.api.dto.request.RefreshTokenRequest;
import dev.booking.sports.identity.api.dto.request.RegisterRequest;
import dev.booking.sports.identity.api.dto.request.ResetPasswordRequest;
import dev.booking.sports.identity.api.dto.response.TokenResponse;
import dev.booking.sports.identity.application.AuthService;
import dev.booking.sports.identity.application.EmailVerificationService;
import dev.booking.sports.identity.application.PasswordChangeService;
import dev.booking.sports.identity.application.PasswordResetService;
import dev.booking.sports.identity.infrastructure.security.AuthPrincipal;
import dev.booking.sports.shared.api.ApiResponse;
import dev.booking.sports.shared.ratelimit.RateLimit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;
	private final EmailVerificationService emailVerificationService;
	private final PasswordResetService passwordResetService;
	private final PasswordChangeService passwordChangeService;

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Register an account and send the activation link by email")
	public ApiResponse<Void> register(
			@Valid @RequestBody RegisterRequest request,
			HttpServletRequest servletRequest) {

		authService.register(request);
		return ApiResponse.success(
				"Registration successful, check your email to activate the account",
				servletRequest.getRequestURI());
	}

	@GetMapping("/verify-email")
	@Operation(summary = "Activate an account using the token from the activation link")
	public ApiResponse<Void> verifyEmail(@RequestParam String token, HttpServletRequest servletRequest) {
		emailVerificationService.verify(token);
		return ApiResponse.success("Account activated, you can sign in now", servletRequest.getRequestURI());
	}

	@PostMapping("/resend-verification")
	@RateLimit(action = "auth.resend-verification", maxRequests = 3, timeWindow = 3600)
	@Operation(summary = "Send the activation link again")
	public ApiResponse<Void> resendVerification(
			@Valid @RequestBody EmailOnlyRequest request,
			HttpServletRequest servletRequest) {

		emailVerificationService.resend(request.email());
		return ApiResponse.success("Email has been sent", servletRequest.getRequestURI());
	}

	@PostMapping("/login")
	@RateLimit(action = "auth.login", maxRequests = 10, timeWindow = 900)
	@Operation(summary = "Sign in with email and password")
	public ApiResponse<TokenResponse> login(
			@Valid @RequestBody LoginRequest request,
			@RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
			HttpServletRequest servletRequest) {

		TokenResponse tokens = authService.login(request, userAgent, clientIpOf(servletRequest));
		return ApiResponse.success(tokens, servletRequest.getRequestURI());
	}

	@PostMapping("/refresh")
	@Operation(summary = "Exchange a refresh token for a new token pair, keeping the same session")
	public ApiResponse<TokenResponse> refresh(
			@Valid @RequestBody RefreshTokenRequest request,
			HttpServletRequest servletRequest) {

		return ApiResponse.success(authService.refresh(request.refreshToken()), servletRequest.getRequestURI());
	}

	@PostMapping("/logout")
	@Operation(summary = "Sign out of the current device only")
	public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthPrincipal principal) {
		authService.logout(principal.userId(), principal.jti());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/logout-all")
	@Operation(summary = "Sign out of every device")
	public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal AuthPrincipal principal) {
		authService.logoutAll(principal.userId());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/forgot-password")
	@RateLimit(action = "auth.forgot-password", maxRequests = 3, timeWindow = 3600)
	@Operation(summary = "Send a one-time code for resetting a forgotten password")
	public ApiResponse<Void> forgotPassword(
			@Valid @RequestBody EmailOnlyRequest request,
			HttpServletRequest servletRequest) {

		passwordResetService.requestReset(request.email());
		return ApiResponse.success("Email has been sent", servletRequest.getRequestURI());
	}

	@PostMapping("/reset-password")
	@Operation(summary = "Reset the password with the one-time code, revoking every session")
	public ApiResponse<Void> resetPassword(
			@Valid @RequestBody ResetPasswordRequest request,
			HttpServletRequest servletRequest) {

		passwordResetService.reset(request);
		return ApiResponse.success("Password reset, please sign in again", servletRequest.getRequestURI());
	}

	@PostMapping("/change-password/request-otp")
	@RateLimit(action = "auth.change-password-otp", maxRequests = 3, timeWindow = 3600)
	@Operation(summary = "Send a one-time code to confirm a password change")
	public ApiResponse<Void> requestChangePasswordOtp(
			@AuthenticationPrincipal AuthPrincipal principal,
			@Valid @RequestBody ChangePasswordOtpRequest request,
			HttpServletRequest servletRequest) {

		passwordChangeService.requestOtp(principal.userId(), request.currentPassword());
		return ApiResponse.success(
				"A verification code has been sent to your email",
				servletRequest.getRequestURI());
	}

	@PostMapping("/change-password")
	@Operation(summary = "Change the password with the one-time code, revoking every session")
	public ApiResponse<Void> changePassword(
			@AuthenticationPrincipal AuthPrincipal principal,
			@Valid @RequestBody ChangePasswordRequest request,
			HttpServletRequest servletRequest) {

		passwordChangeService.changePassword(principal.userId(), request);
		return ApiResponse.success(
				"Password changed, all devices were signed out, please sign in again",
				servletRequest.getRequestURI());
	}


	private String clientIpOf(HttpServletRequest request) {
		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (forwardedFor != null && !forwardedFor.isBlank()) {
			return forwardedFor.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}
}
