package dev.booking.sports.shared.exception;

import org.springframework.http.HttpStatus;

public interface ErrorCode {

	String code();

	HttpStatus status();

	String defaultMessage();
}
