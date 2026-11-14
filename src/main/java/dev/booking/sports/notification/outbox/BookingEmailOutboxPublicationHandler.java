package dev.booking.sports.notification.outbox;

import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import dev.booking.sports.notification.email.BookingEmailMessageTypes;
import dev.booking.sports.notification.email.EmailMessage;
import dev.booking.sports.notification.email.EmailQueueMessage;
import dev.booking.sports.notification.service.EmailQueuePublisher;
import dev.booking.sports.shared.outbox.OutboxEvent;
import dev.booking.sports.shared.outbox.OutboxPublicationHandler;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class BookingEmailOutboxPublicationHandler implements OutboxPublicationHandler {
    private final ObjectMapper mapper;
    private final EmailQueuePublisher publisher;

    @Override
    public boolean supports(String eventType) {
        return BookingEmailMessageTypes.REQUESTED.equals(eventType);
    }

    @Override
    public void publish(OutboxEvent event) {
        Assert.isTrue(supports(event.getEventType())
                && BookingEmailMessageTypes.AGGREGATE_TYPE.equals(event.getAggregateType())
                && event.getEventVersion() == BookingEmailMessageTypes.VERSION, "Unsupported booking email contract");
        var email = mapper.readValue(event.getPayload(), EmailMessage.class);
        publisher.publish(new EmailQueueMessage(event.getEventId(), email));
    }
}
