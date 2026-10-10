package dev.booking.sports.shared.ratelimit;

import org.springframework.http.HttpStatus;

import dev.booking.sports.shared.exception.ErrorCode;

public enum RateLimitErrorCode implements ErrorCode {

	TOO_MANY_REQUESTS;

	@Override
	public String code() {
		return name();
	}

	@Override
	public HttpStatus status() {
		return HttpStatus.TOO_MANY_REQUESTS;
	}

	@Override
	public String defaultMessage() {
		return "Too many requests, please try again later";
	}
}
