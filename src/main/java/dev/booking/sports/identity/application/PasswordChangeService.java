package dev.booking.sports.identity.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.booking.sports.identity.api.dto.request.ChangePasswordRequest;
import dev.booking.sports.identity.api.error.AuthErrorCode;
import dev.booking.sports.identity.domain.event.IdentityOutboxMessageTypes;
import dev.booking.sports.identity.domain.event.PasswordChangeOtpIssuedEvent;
import dev.booking.sports.identity.domain.event.PasswordChangedEvent;
import dev.booking.sports.identity.domain.model.User;
import dev.booking.sports.identity.domain.repository.UserRepository;
import dev.booking.sports.identity.infrastructure.redis.OtpPurpose;
import dev.booking.sports.identity.infrastructure.security.ApplicationUserDetailsService;
import dev.booking.sports.shared.exception.ApiException;
import dev.booking.sports.shared.outbox.OutboxService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PasswordChangeService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final OtpService otpService;
	private final AuthSessionService sessionService;
	private final ApplicationUserDetailsService userDetailsService;
	private final OutboxService outboxService;

	@Transactional
	public void requestOtp(UUID userId, String currentPassword) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ApiException(AuthErrorCode.TOKEN_REVOKED));

		if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
			throw new ApiException(AuthErrorCode.INVALID_CURRENT_PASSWORD);
		}

		String otp = otpService.issue(OtpPurpose.PASSWORD_CHANGE, userId);
		outboxService.enqueue(
				IdentityOutboxMessageTypes.USER_AGGREGATE_TYPE,
				userId,
				IdentityOutboxMessageTypes.PASSWORD_CHANGE_OTP_ISSUED,
				IdentityOutboxMessageTypes.EVENT_VERSION,
				new PasswordChangeOtpIssuedEvent(
						userId, user.getEmail(), user.getFullName(), otp, otpService.ttl()));
	}


	@Transactional
	public void changePassword(UUID userId, ChangePasswordRequest request) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ApiException(AuthErrorCode.TOKEN_REVOKED));

		if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
			throw new ApiException(AuthErrorCode.PASSWORD_REUSED);
		}

		otpService.verify(OtpPurpose.PASSWORD_CHANGE, userId, request.otp());

		user.changePassword(passwordEncoder.encode(request.newPassword()), Instant.now());
		userDetailsService.evict(user.getEmail());
		sessionService.revokeAllSessions(userId);

		outboxService.enqueue(
				IdentityOutboxMessageTypes.USER_AGGREGATE_TYPE,
				userId,
				IdentityOutboxMessageTypes.PASSWORD_CHANGED,
				IdentityOutboxMessageTypes.EVENT_VERSION,
				new PasswordChangedEvent(userId, user.getEmail(), user.getFullName()));
	}
}
