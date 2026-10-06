package dev.booking.sports.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.booking.sports.identity.api.error.AuthErrorCode;
import dev.booking.sports.identity.config.AuthProperties;
import dev.booking.sports.identity.infrastructure.redis.OtpPurpose;
import dev.booking.sports.identity.infrastructure.redis.OtpStore;
import dev.booking.sports.identity.infrastructure.security.TokenHasher;
import dev.booking.sports.shared.exception.ApiException;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

	private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

	@Mock
	private OtpStore otpStore;

	private OtpService otpService;

	@BeforeEach
	void setUp() {
		AuthProperties properties = new AuthProperties(
				Duration.ofHours(24),
				new AuthProperties.Otp(6, Duration.ofMinutes(10), 5));
		otpService = new OtpService(otpStore, properties);
	}

	@Test
	void issue_storesHashedOtp() {
		String otp = otpService.issue(OtpPurpose.PASSWORD_RESET, USER_ID);

		assertThat(otp).hasSize(6).matches("\\d{6}");
		verify(otpStore).issue(
				eq(OtpPurpose.PASSWORD_RESET),
				eq(USER_ID),
				eq(TokenHasher.sha256Hex(USER_ID + ":" + otp)),
				eq(Duration.ofMinutes(10)));
	}

	@Test
	void verify_mapsStoreResultsToErrorCodes() {
		when(otpStore.verify(any(), any(), any(), eq(5)))
				.thenReturn(OtpStore.VerificationResult.NOT_FOUND);
		assertApiError(() -> otpService.verify(OtpPurpose.PASSWORD_RESET, USER_ID, "000000"), AuthErrorCode.OTP_NOT_FOUND);

		when(otpStore.verify(any(), any(), any(), eq(5)))
				.thenReturn(OtpStore.VerificationResult.INVALID);
		assertApiError(() -> otpService.verify(OtpPurpose.PASSWORD_RESET, USER_ID, "000000"), AuthErrorCode.OTP_INVALID);

		when(otpStore.verify(any(), any(), any(), eq(5)))
				.thenReturn(OtpStore.VerificationResult.TOO_MANY_ATTEMPTS);
		assertApiError(
				() -> otpService.verify(OtpPurpose.PASSWORD_RESET, USER_ID, "000000"),
				AuthErrorCode.OTP_TOO_MANY_ATTEMPTS);
	}

	private void assertApiError(Runnable action, AuthErrorCode expected) {
		assertThatThrownBy(action::run)
				.isInstanceOf(ApiException.class)
				.extracting(exception -> ((ApiException) exception).getErrorCode())
				.isEqualTo(expected);
	}
}
