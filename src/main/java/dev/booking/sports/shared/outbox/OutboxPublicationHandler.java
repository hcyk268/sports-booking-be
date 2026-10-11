package dev.booking.sports.shared.outbox;

public interface OutboxPublicationHandler {

	boolean supports(String eventType);

	void publish(OutboxEvent message) throws Exception;
}
