package dev.booking.sports.notification.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import dev.booking.sports.identity.domain.event.EmailVerificationRequestedEvent;
import dev.booking.sports.identity.domain.event.IdentityOutboxMessageTypes;
import dev.booking.sports.identity.domain.event.PasswordChangeOtpIssuedEvent;
import dev.booking.sports.identity.domain.event.PasswordChangedEvent;
import dev.booking.sports.identity.domain.event.PasswordResetOtpIssuedEvent;
import dev.booking.sports.notification.service.EmailQueuePublisher;
import dev.booking.sports.notification.service.EmailTemplateService;
import dev.booking.sports.notification.service.EmailTemplateService.RenderedEmail;
import dev.booking.sports.shared.outbox.OutboxEvent;
import dev.booking.sports.shared.outbox.OutboxPublicationHandler;

@RequiredArgsConstructor
@Component
public class IdentityEmailOutboxPublicationHandler implements OutboxPublicationHandler {

	private final ObjectMapper objectMapper;
	private final EmailTemplateService emailTemplateService;
	private final EmailQueuePublisher emailQueuePublisher;


	@Override
	public boolean supports(String eventType) {
		return IdentityOutboxMessageTypes.EMAIL_VERIFICATION_REQUESTED.equals(eventType)
				|| IdentityOutboxMessageTypes.PASSWORD_RESET_OTP_ISSUED.equals(eventType)
				|| IdentityOutboxMessageTypes.PASSWORD_CHANGE_OTP_ISSUED.equals(eventType)
				|| IdentityOutboxMessageTypes.PASSWORD_CHANGED.equals(eventType);
	}

	@Override
	public void publish(OutboxEvent message) throws Exception {
		String payloadJson = message.getPayload();

		RenderedEmail rendered = switch (message.getEventType()) {
			case IdentityOutboxMessageTypes.EMAIL_VERIFICATION_REQUESTED -> {
				EmailVerificationRequestedEvent event = objectMapper.readValue(
						payloadJson, EmailVerificationRequestedEvent.class);
				yield emailTemplateService.verification(event.fullName(), event.verificationUrl());
			}
			case IdentityOutboxMessageTypes.PASSWORD_RESET_OTP_ISSUED -> {
				PasswordResetOtpIssuedEvent event = objectMapper.readValue(
						payloadJson, PasswordResetOtpIssuedEvent.class);
				yield emailTemplateService.passwordReset(event.fullName(), event.otp(), event.ttl());
			}
			case IdentityOutboxMessageTypes.PASSWORD_CHANGE_OTP_ISSUED -> {
				PasswordChangeOtpIssuedEvent event = objectMapper.readValue(
						payloadJson, PasswordChangeOtpIssuedEvent.class);
				yield emailTemplateService.passwordChangeOtp(event.fullName(), event.otp(), event.ttl());
			}
			case IdentityOutboxMessageTypes.PASSWORD_CHANGED -> {
				PasswordChangedEvent event = objectMapper.readValue(
						payloadJson, PasswordChangedEvent.class);
				yield emailTemplateService.passwordChanged(event.fullName());
			}
			default -> throw new IllegalStateException("Unsupported event type " + message.getEventType());
		};

		JsonNode payload = objectMapper.readTree(payloadJson);
		String email = payload.required("email").asString();
		String fullName = payload.required("fullName").asString();
		emailQueuePublisher.publish(email, fullName, rendered);
	}
}
