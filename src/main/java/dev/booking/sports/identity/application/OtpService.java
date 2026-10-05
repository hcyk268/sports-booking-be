package dev.booking.sports.identity.application;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

import org.springframework.stereotype.Service;

import dev.booking.sports.identity.api.error.AuthErrorCode;
import dev.booking.sports.identity.config.AuthProperties;
import dev.booking.sports.identity.infrastructure.redis.OtpPurpose;
import dev.booking.sports.identity.infrastructure.redis.OtpStore;
import dev.booking.sports.identity.infrastructure.security.TokenHasher;
import dev.booking.sports.shared.exception.ApiException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OtpService {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final OtpStore otpStore;
	private final AuthProperties properties;

	public String issue(OtpPurpose purpose, UUID userId) {
		String otp = randomNumericCode(properties.otp().length());
		otpStore.issue(purpose, userId, hash(userId, otp), properties.otp().ttl());
		return otp;
	}

	public void verify(OtpPurpose purpose, UUID userId, String otp) {
		OtpStore.VerificationResult result = otpStore.verify(
				purpose, userId, hash(userId, otp), properties.otp().maxAttempts());

		switch (result) {
			case OK -> {
			}
			case NOT_FOUND -> throw new ApiException(AuthErrorCode.OTP_NOT_FOUND);
			case INVALID -> throw new ApiException(AuthErrorCode.OTP_INVALID);
			case TOO_MANY_ATTEMPTS -> throw new ApiException(AuthErrorCode.OTP_TOO_MANY_ATTEMPTS);
		}
	}

	public Duration ttl() {
		return properties.otp().ttl();
	}

	/**
	 * The user id acts as a salt so a leaked store cannot be attacked with one precomputed table
	 * covering every possible code.
	 */
	private String hash(UUID userId, String otp) {
		return TokenHasher.sha256Hex(userId + ":" + otp);
	}

	private String randomNumericCode(int length) {
		StringBuilder code = new StringBuilder(length);
		for (int index = 0; index < length; index++) {
			code.append(RANDOM.nextInt(10));
		}
		return code.toString();
	}
}
