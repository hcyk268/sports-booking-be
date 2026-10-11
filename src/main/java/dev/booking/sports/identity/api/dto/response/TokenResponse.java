package dev.booking.sports.identity.api.dto.response;

public record TokenResponse(
		String accessToken,
		String refreshToken,
		String tokenType,
		long expiresIn,
		UserSummaryResponse user) {

	private static final String BEARER = "Bearer";

	public static TokenResponse of(String accessToken, String refreshToken, long expiresIn, UserSummaryResponse user) {
		return new TokenResponse(accessToken, refreshToken, BEARER, expiresIn, user);
	}
}
