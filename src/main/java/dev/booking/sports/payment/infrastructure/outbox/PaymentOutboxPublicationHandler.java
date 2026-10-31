package dev.booking.sports.payment.infrastructure.outbox;

import dev.booking.sports.payment.infrastructure.messaging.PaymentEventMessage;
import dev.booking.sports.payment.infrastructure.messaging.PaymentEventPublisher;
import org.springframework.stereotype.Component;
import dev.booking.sports.payment.domain.event.*;
import dev.booking.sports.shared.outbox.*;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class PaymentOutboxPublicationHandler implements OutboxPublicationHandler {

    private final ObjectMapper mapper;
    private final PaymentEventPublisher publisher;

    @Override
    public boolean supports(String type) {
        return PaymentOutboxMessageTypes.SUCCEEDED.equals(type)
                || PaymentOutboxMessageTypes.REVIEW_REQUIRED.equals(type);
    }

    @Override
    public void publish(OutboxEvent event) throws Exception {
        String aggregate = PaymentOutboxMessageTypes.SUCCEEDED.equals(event.getEventType()) ? "PAYMENT" : "PAYMENT_INBOX";
        if (!supports(event.getEventType()) || event.getEventVersion() != PaymentOutboxMessageTypes.VERSION
                || !aggregate.equals(event.getAggregateType())) throw new IllegalArgumentException("Invalid payment event contract");
        if (PaymentOutboxMessageTypes.SUCCEEDED.equals(event.getEventType())) {
            mapper.readValue(event.getPayload(), PaymentSucceededEvent.class);
        } else {
            mapper.readValue(event.getPayload(), PaymentReviewRequiredEvent.class);
        }
        publisher.publish(new PaymentEventMessage(event.getEventId(), event.getAggregateType(),
                event.getAggregateId(), event.getEventType(), event.getEventVersion(), mapper.readTree(event.getPayload())));
    }
}
