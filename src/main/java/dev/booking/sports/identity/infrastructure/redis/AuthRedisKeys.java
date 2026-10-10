package dev.booking.sports.identity.infrastructure.redis;

import java.util.UUID;


public final class AuthRedisKeys {

	private AuthRedisKeys() {
	}

	public static String tokenVersion(UUID userId) {
		return "auth:tv:" + userId;
	}

	public static String session(UUID userId, String jti) {
		return "auth:session:" + userId + ":" + jti;
	}

	public static String sessionIndex(UUID userId) {
		return "auth:sessions:" + userId;
	}

	public static String accessTokenBlacklist(UUID userId, String jti) {
		return "auth:blacklist:access:" + userId + ":" + jti;
	}

	public static String emailVerification(String tokenHash) {
		return "auth:verify:" + tokenHash;
	}

	public static String otp(OtpPurpose purpose, UUID userId) {
		return otp(purpose, userId, null);
	}

	public static String otp(OtpPurpose purpose, UUID userId, String scope) {
		if (scope == null || scope.isBlank()) {
			return "auth:otp:" + purpose.segment() + ":" + userId;
		}
		return "auth:otp:" + purpose.segment() + ":" + userId + ":" + scope;
	}

	public static String rolePermissionChangePending(UUID actorUserId, String roleCode) {
		return "auth:rbac-pending:" + actorUserId + ":" + roleCode;
	}
}
