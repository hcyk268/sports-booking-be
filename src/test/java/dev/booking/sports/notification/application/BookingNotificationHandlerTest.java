package dev.booking.sports.notification.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import dev.booking.sports.identity.publicapi.EmailRecipient;
import dev.booking.sports.identity.publicapi.EmailRecipientDirectory;
import dev.booking.sports.notification.service.BookingEmailTemplateService;
import dev.booking.sports.notification.service.EmailTemplateService.RenderedEmail;
import dev.booking.sports.shared.outbox.OutboxService;
import dev.booking.sports.notification.email.BookingEmailMessageTypes;
import dev.booking.sports.notification.email.EmailMessage;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import dev.booking.sports.notification.BookingNotificationFixtures;
import dev.booking.sports.notification.consumer.BookingNotificationMessage;
import dev.booking.sports.notification.domain.repository.NotificationRepository;
import tools.jackson.databind.ObjectMapper;

class BookingNotificationHandlerTest {
    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final EmailRecipientDirectory recipients = mock(EmailRecipientDirectory.class);
    private final BookingEmailTemplateService templates = mock(BookingEmailTemplateService.class);
    private final OutboxService outbox = mock(OutboxService.class);
    private final org.springframework.context.ApplicationEventPublisher events = mock(org.springframework.context.ApplicationEventPublisher.class);
    private final BookingNotificationHandler handler = new BookingNotificationHandler(new ObjectMapper(), repository, recipients, templates, outbox, events);

    @ParameterizedTest
    @CsvSource({ "booking.confirmed,BOOKING_CONFIRMED", "booking.cancelled,BOOKING_CANCELLED", "booking.expired,BOOKING_EXPIRED" })
    void mapsSupportedEventsToRecipientResourceAndDedupKey(String eventType, String notificationType) {
        UUID recipient = UUID.randomUUID();
        var event = BookingNotificationFixtures.message(recipient, eventType);
        when(repository.insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(recipients.findByUserId(recipient)).thenReturn(Optional.of(new EmailRecipient("customer@example.com", "Customer")));
        when(templates.render(eq("Customer"), anyString(), anyString(), any())).thenReturn(new RenderedEmail("Subject", "HTML"));
        handler.handle(event);
        verify(repository).insertIfAbsent(any(UUID.class), eq(recipient), eq(notificationType), anyString(),
                contains("BK-TEST-001"), eq("BOOKING"), eq(UUID.fromString(event.aggregateId())), eq(event.eventId()));
        verify(outbox).enqueue(eq(BookingEmailMessageTypes.AGGREGATE_TYPE), any(UUID.class),
                eq(BookingEmailMessageTypes.REQUESTED), eq(1), eq(new EmailMessage("customer@example.com", "Customer", "Subject", "HTML")));
    }

    @ParameterizedTest
    @ValueSource(strings = { "version", "aggregate", "id", "type", "payload", "eventId" })
    void rejectsInvalidContractsBeforeInserting(String invalidPart) {
        var event = BookingNotificationFixtures.message(UUID.randomUUID(), "booking.confirmed");
        var invalid = new BookingNotificationMessage(
                invalidPart.equals("eventId") ? null : event.eventId(),
                invalidPart.equals("aggregate") ? "PAYMENT" : event.aggregateType(),
                invalidPart.equals("id") ? UUID.randomUUID().toString() : event.aggregateId(),
                invalidPart.equals("type") ? "booking.held" : event.eventType(),
                invalidPart.equals("version") ? 2 : event.eventVersion(),
                invalidPart.equals("payload") ? null : event.payload());
        assertThatThrownBy(() -> handler.handle(invalid)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    @Test void redeliveryDoesNotQueueAnotherEmailOrLookUpRecipient() {
        handler.handle(BookingNotificationFixtures.message(UUID.randomUUID(), "booking.confirmed"));
        verifyNoInteractions(recipients, templates, outbox, events);
    }
}
