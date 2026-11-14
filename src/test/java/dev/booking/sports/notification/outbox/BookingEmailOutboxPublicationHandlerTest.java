package dev.booking.sports.notification.outbox;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import dev.booking.sports.notification.email.*;
import dev.booking.sports.notification.service.EmailQueuePublisher;
import dev.booking.sports.shared.outbox.OutboxEvent;
import tools.jackson.databind.ObjectMapper;

class BookingEmailOutboxPublicationHandlerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final EmailQueuePublisher publisher = mock(EmailQueuePublisher.class);
    private final BookingEmailOutboxPublicationHandler handler = new BookingEmailOutboxPublicationHandler(mapper, publisher);

    @Test void republishesSnapshotWithStableOutboxIdForEmailDeduplication() {
        var email = new EmailMessage("test@example.com", "Customer", "Booking confirmed", "<p>Details</p>");
        var event = new OutboxEvent("NOTIFICATION", UUID.randomUUID().toString(), BookingEmailMessageTypes.REQUESTED, 1,
                mapper.writeValueAsString(email));
        handler.publish(event);
        handler.publish(event);
        verify(publisher, times(2)).publish(new EmailQueueMessage(event.getEventId(), email));
        assertThat(handler.supports("booking.confirmed")).isFalse();
        assertThat(handler.supports(BookingEmailMessageTypes.REQUESTED)).isTrue();
    }

    @Test void rejectsUnsupportedVersionBeforePublishing() {
        var event = new OutboxEvent("NOTIFICATION", UUID.randomUUID().toString(), BookingEmailMessageTypes.REQUESTED, 2, "{}");
        assertThatThrownBy(() -> handler.publish(event)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(publisher);
    }
}
