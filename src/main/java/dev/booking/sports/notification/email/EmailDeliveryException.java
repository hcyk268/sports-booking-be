package dev.booking.sports.notification.email;

public class EmailDeliveryException extends RuntimeException {
	private final boolean retryable;

	public EmailDeliveryException(String message) {
		this(message, true);
	}

	public EmailDeliveryException(String message, boolean retryable) {
		super(message);
		this.retryable = retryable;
	}

	public EmailDeliveryException(String message, Throwable cause) {
		super(message, cause);
		this.retryable = true;
	}

	public boolean isRetryable() { return retryable; }
}
