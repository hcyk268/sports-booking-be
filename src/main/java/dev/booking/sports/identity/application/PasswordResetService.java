package dev.booking.sports.identity.application;

import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.booking.sports.identity.api.dto.request.ResetPasswordRequest;
import dev.booking.sports.identity.api.error.AuthErrorCode;
import dev.booking.sports.identity.domain.enums.UserStatus;
import dev.booking.sports.identity.domain.event.IdentityOutboxMessageTypes;
import dev.booking.sports.identity.domain.event.PasswordChangedEvent;
import dev.booking.sports.identity.domain.event.PasswordResetOtpIssuedEvent;
import dev.booking.sports.identity.domain.model.User;
import dev.booking.sports.identity.domain.repository.UserRepository;
import dev.booking.sports.identity.infrastructure.redis.OtpPurpose;
import dev.booking.sports.identity.infrastructure.security.ApplicationUserDetailsService;
import dev.booking.sports.shared.exception.ApiException;
import dev.booking.sports.shared.outbox.OutboxService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final OtpService otpService;
	private final AuthSessionService sessionService;
	private final ApplicationUserDetailsService userDetailsService;
	private final OutboxService outboxService;

	@Transactional
	public void requestReset(String rawEmail) {
		String email = EmailNormalizer.normalize(rawEmail);

		userRepository.findByEmailIgnoreCase(email)
				.filter(user -> user.getStatus() == UserStatus.ACTIVE)
				.ifPresent(user -> {
					String otp = otpService.issue(OtpPurpose.PASSWORD_RESET, user.getId());
					outboxService.enqueue(
							IdentityOutboxMessageTypes.USER_AGGREGATE_TYPE,
							user.getId(),
							IdentityOutboxMessageTypes.PASSWORD_RESET_OTP_ISSUED,
							IdentityOutboxMessageTypes.EVENT_VERSION,
							new PasswordResetOtpIssuedEvent(
									user.getId(), user.getEmail(), user.getFullName(), otp, otpService.ttl()));
				});
	}

	@Transactional
	public void reset(ResetPasswordRequest request) {
		String email = EmailNormalizer.normalize(request.email());

		User user = userRepository.findByEmailIgnoreCase(email)
				.orElseThrow(() -> new ApiException(AuthErrorCode.OTP_NOT_FOUND));

		if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
			throw new ApiException(AuthErrorCode.PASSWORD_REUSED);
		}

		otpService.verify(OtpPurpose.PASSWORD_RESET, user.getId(), request.otp());

		user.changePassword(passwordEncoder.encode(request.newPassword()), Instant.now());
		userDetailsService.evict(user.getEmail());
		sessionService.revokeAllSessions(user.getId());

		outboxService.enqueue(
				IdentityOutboxMessageTypes.USER_AGGREGATE_TYPE,
				user.getId(),
				IdentityOutboxMessageTypes.PASSWORD_CHANGED,
				IdentityOutboxMessageTypes.EVENT_VERSION,
				new PasswordChangedEvent(user.getId(), user.getEmail(), user.getFullName()));
	}
}
