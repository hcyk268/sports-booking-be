package dev.booking.sports.identity.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.booking.sports.identity.api.error.AuthErrorCode;
import dev.booking.sports.identity.config.AuthProperties;
import dev.booking.sports.identity.config.FrontendProperties;
import dev.booking.sports.identity.domain.enums.UserStatus;
import dev.booking.sports.identity.domain.event.EmailVerificationRequestedEvent;
import dev.booking.sports.identity.domain.event.IdentityOutboxMessageTypes;
import dev.booking.sports.identity.domain.model.User;
import dev.booking.sports.identity.domain.repository.UserRepository;
import dev.booking.sports.identity.infrastructure.redis.EmailVerificationTokenStore;
import dev.booking.sports.identity.infrastructure.security.TokenHasher;
import dev.booking.sports.shared.exception.ApiException;
import dev.booking.sports.shared.outbox.OutboxService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

	private final UserRepository userRepository;
	private final EmailVerificationTokenStore tokenStore;
	private final AuthProperties authProperties;
	private final FrontendProperties frontendProperties;
	private final OutboxService outboxService;

	public void sendVerificationLink(User user) {
		String token = TokenHasher.randomUrlSafeToken();
		tokenStore.issue(TokenHasher.sha256Hex(token), user.getId(), authProperties.verificationTokenTtl());

		outboxService.enqueue(
				IdentityOutboxMessageTypes.USER_AGGREGATE_TYPE,
				user.getId(),
				IdentityOutboxMessageTypes.EMAIL_VERIFICATION_REQUESTED,
				IdentityOutboxMessageTypes.EVENT_VERSION,
				new EmailVerificationRequestedEvent(
						user.getId(),
						user.getEmail(),
						user.getFullName(),
						frontendProperties.verifyEmailUrl(token)));
	}

	@Transactional
	public void verify(String token) {
		UUID userId = tokenStore.consume(TokenHasher.sha256Hex(token))
				.orElseThrow(() -> new ApiException(AuthErrorCode.VERIFICATION_TOKEN_INVALID));

		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ApiException(AuthErrorCode.VERIFICATION_TOKEN_INVALID));

		if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
			user.activate(Instant.now());
		}
	}

	@Transactional
	public void resend(String rawEmail) {
		String email = EmailNormalizer.normalize(rawEmail);

		userRepository.findByEmailIgnoreCase(email)
				.filter(user -> user.getStatus() == UserStatus.PENDING_VERIFICATION)
				.ifPresent(this::sendVerificationLink);
	}
}
