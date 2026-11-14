package dev.booking.sports.notification.application;

import java.util.UUID;
import dev.booking.sports.identity.publicapi.EmailRecipientDirectory;
import dev.booking.sports.notification.email.BookingEmailDetails;
import dev.booking.sports.notification.email.BookingEmailMessageTypes;
import dev.booking.sports.notification.email.EmailMessage;
import dev.booking.sports.notification.service.BookingEmailTemplateService;
import dev.booking.sports.shared.outbox.OutboxService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import dev.booking.sports.booking.domain.event.BookingCancelledEvent;
import dev.booking.sports.booking.domain.event.BookingConfirmedEvent;
import dev.booking.sports.booking.domain.event.BookingExpiredEvent;
import dev.booking.sports.booking.domain.event.BookingOutboxMessageTypes;
import dev.booking.sports.notification.consumer.BookingNotificationMessage;
import dev.booking.sports.notification.domain.model.Notification;
import dev.booking.sports.notification.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import dev.booking.sports.notification.realtime.NotificationChangedEvent;

@Service
@RequiredArgsConstructor
public class BookingNotificationHandler {

    private final ObjectMapper objectMapper;
    private final NotificationRepository repository;
    private final EmailRecipientDirectory recipients;
    private final BookingEmailTemplateService templates;
    private final OutboxService outbox;
    private final ApplicationEventPublisher events;

    @Transactional
    public void handle(BookingNotificationMessage message) {
        Assert.notNull(message, "Booking event is required");
        Assert.notNull(message.eventId(), "Event ID is required");
        Assert.isTrue(BookingOutboxMessageTypes.BOOKING_AGGREGATE_TYPE.equals(message.aggregateType()),
                "Expected BOOKING aggregate");
        Assert.isTrue(message.eventVersion() == BookingOutboxMessageTypes.EVENT_VERSION,
                "Unsupported booking event version");
        Assert.hasText(message.aggregateId(), "Aggregate ID is required");
        Assert.hasText(message.eventType(), "Event type is required");
        Assert.isTrue(message.payload() != null && message.payload().isObject(), "Booking payload must be an object");

        Plan plan = switch (message.eventType()) {
            case BookingOutboxMessageTypes.BOOKING_CONFIRMED -> {
                var event = objectMapper.treeToValue(message.payload(), BookingConfirmedEvent.class);
                yield new Plan(notification(message, event.bookingId(), event.customerId(), "BOOKING_CONFIRMED",
                        "Đặt sân thành công", "Đơn đặt sân " + event.bookingCode() + " đã được xác nhận."),
                        new BookingEmailDetails(event.bookingCode(), event.totalAmount(), event.currency(), event.slots(), null));
            }
            case BookingOutboxMessageTypes.BOOKING_CANCELLED -> {
                var event = objectMapper.treeToValue(message.payload(), BookingCancelledEvent.class);
                yield new Plan(notification(message, event.bookingId(), event.customerId(), "BOOKING_CANCELLED",
                        "Đặt sân đã bị hủy", "Đơn đặt sân " + event.bookingCode() + " đã bị hủy."),
                        new BookingEmailDetails(event.bookingCode(), event.totalAmount(), event.currency(), event.slots(), event.cancellationReason()));
            }
            case BookingOutboxMessageTypes.BOOKING_EXPIRED -> {
                var event = objectMapper.treeToValue(message.payload(), BookingExpiredEvent.class);
                yield new Plan(notification(message, event.bookingId(), event.customerId(), "BOOKING_EXPIRED",
                        "Đặt sân đã hết hạn", "Đơn đặt sân " + event.bookingCode() + " đã hết thời gian thanh toán."),
                        new BookingEmailDetails(event.bookingCode(), event.totalAmount(), event.currency(), event.slots(), null));
            }
            default -> throw new IllegalArgumentException("Unsupported booking notification event type");
        };
        // A redelivery or concurrent delivery is a successful no-op, without aborting the transaction.
        var notification = plan.notification();
        UUID notificationId = UUID.randomUUID();
        int inserted = repository.insertIfAbsent(notificationId, notification.getRecipientId(), notification.getType(),
                notification.getTitle(), notification.getBody(), notification.getResourceType(),
                notification.getResourceId(), notification.getSourceEventId());
        if (inserted == 0) return;

        var recipient = recipients.findByUserId(notification.getRecipientId())
                .orElseThrow(() -> new IllegalStateException("Booking email recipient is unavailable"));
        var rendered = templates.render(recipient.fullName(), notification.getTitle(), notification.getBody(), plan.details());
        outbox.enqueue(BookingEmailMessageTypes.AGGREGATE_TYPE, notificationId, BookingEmailMessageTypes.REQUESTED,
                BookingEmailMessageTypes.VERSION,
                new EmailMessage(recipient.email(), recipient.fullName(), rendered.subject(), rendered.htmlBody()));
        events.publishEvent(new NotificationChangedEvent(UUID.randomUUID(), notification.getRecipientId(),
                NotificationChangedEvent.Action.CREATED, notificationId));
    }

    private record Plan(Notification notification, BookingEmailDetails details) {}

    private Notification notification(BookingNotificationMessage message, UUID bookingId, UUID recipientId,
            String type, String title, String body) {
        Assert.isTrue(UUID.fromString(message.aggregateId()).equals(bookingId),
                "Booking ID must match the envelope aggregate ID");
        return Notification.create(recipientId, type, title, body, "BOOKING", bookingId, message.eventId());
    }
}
