package dev.booking.sports.notification.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tools.jackson.databind.ObjectMapper;

import dev.booking.sports.identity.domain.event.EmailVerificationRequestedEvent;
import dev.booking.sports.identity.domain.event.IdentityOutboxMessageTypes;
import dev.booking.sports.notification.service.EmailQueuePublisher;
import dev.booking.sports.notification.service.EmailTemplateService;
import dev.booking.sports.notification.service.EmailTemplateService.RenderedEmail;
import dev.booking.sports.shared.outbox.OutboxEvent;

@ExtendWith(MockitoExtension.class)
class IdentityEmailOutboxPublicationHandlerTest {

	private static final UUID USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

	@Mock
	private EmailTemplateService emailTemplateService;

	@Mock
	private EmailQueuePublisher emailQueuePublisher;

	private ObjectMapper objectMapper;
	private IdentityEmailOutboxPublicationHandler handler;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		handler = new IdentityEmailOutboxPublicationHandler(objectMapper, emailTemplateService, emailQueuePublisher);
	}

	@Test
	void supports_onlyIdentityEmailEvents() {
		assertThat(handler.supports(IdentityOutboxMessageTypes.EMAIL_VERIFICATION_REQUESTED)).isTrue();
		assertThat(handler.supports(IdentityOutboxMessageTypes.PASSWORD_CHANGED)).isTrue();
		assertThat(handler.supports("booking.created")).isFalse();
	}

	@Test
	void publish_emailVerificationRequested_queuesRenderedEmail() throws Exception {
		var domainEvent = new EmailVerificationRequestedEvent(
				USER_ID,
				"user@example.com",
				"Minh Anh",
				"https://localhost:3000/verify?token=xyz");
		String payload = objectMapper.writeValueAsString(domainEvent);
		OutboxEvent outbox = new OutboxEvent(
				IdentityOutboxMessageTypes.USER_AGGREGATE_TYPE,
				USER_ID.toString(),
				IdentityOutboxMessageTypes.EMAIL_VERIFICATION_REQUESTED,
				IdentityOutboxMessageTypes.EVENT_VERSION,
				payload);

		RenderedEmail rendered = new RenderedEmail("Kích hoạt tài khoản Sports Booking", "<p>Activate</p>");
		when(emailTemplateService.verification("Minh Anh", "https://localhost:3000/verify?token=xyz"))
				.thenReturn(rendered);

		handler.publish(outbox);

		verify(emailQueuePublisher).publish("user@example.com", "Minh Anh", rendered);
	}
}
