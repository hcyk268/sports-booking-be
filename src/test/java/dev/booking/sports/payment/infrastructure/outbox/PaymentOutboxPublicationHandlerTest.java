package dev.booking.sports.payment.infrastructure.outbox;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import dev.booking.sports.payment.domain.event.*;
import dev.booking.sports.payment.infrastructure.messaging.PaymentEventMessage;
import dev.booking.sports.payment.infrastructure.messaging.PaymentEventPublisher;
import dev.booking.sports.shared.outbox.OutboxEvent;
import tools.jackson.databind.ObjectMapper;

class PaymentOutboxPublicationHandlerTest {
    private final PaymentEventPublisher publisher = mock(PaymentEventPublisher.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final PaymentOutboxPublicationHandler handler = new PaymentOutboxPublicationHandler(mapper, publisher);

    @Test void delegatesSucceededEnvelopeWithStableEventId() throws Exception {
        var event = event();
        handler.publish(event);
        verify(publisher).publish(new PaymentEventMessage(event.getEventId(), event.getAggregateType(),
                event.getAggregateId(), event.getEventType(), event.getEventVersion(), mapper.readTree(event.getPayload())));
    }

    @Test void delegatesReviewRequiredEnvelope() throws Exception {
        var inboxId = UUID.randomUUID();
        var event = new OutboxEvent("PAYMENT_INBOX", inboxId.toString(), PaymentOutboxMessageTypes.REVIEW_REQUIRED, 1,
                mapper.writeValueAsString(new PaymentReviewRequiredEvent(inboxId, UUID.randomUUID(), UUID.randomUUID(),
                        "invoice", "AMOUNT_MISMATCH", Instant.now())));

        handler.publish(event);

        verify(publisher).publish(new PaymentEventMessage(event.getEventId(), event.getAggregateType(),
                event.getAggregateId(), event.getEventType(), event.getEventVersion(), mapper.readTree(event.getPayload())));
    }

    @Test void invalidContractNeverReachesPublisher() {
        assertThatThrownBy(() -> handler.publish(new OutboxEvent("OTHER", "id", "payment.succeeded", 1, "{}")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> handler.publish(new OutboxEvent("PAYMENT", "id", "payment.succeeded", 2, "{}")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> handler.publish(new OutboxEvent("PAYMENT", "id", "unknown", 1, "{}")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(publisher);
    }

    @Test void malformedPayloadNeverReachesPublisher() {
        assertThatThrownBy(() -> handler.publish(new OutboxEvent("PAYMENT", "id", "payment.succeeded", 1, "{")))
                .isInstanceOf(tools.jackson.core.JacksonException.class);
        verifyNoInteractions(publisher);
    }

    private OutboxEvent event() {
        var paymentId = UUID.randomUUID();
        return new OutboxEvent("PAYMENT", paymentId.toString(), PaymentOutboxMessageTypes.SUCCEEDED, 1,
                mapper.writeValueAsString(new PaymentSucceededEvent(paymentId, UUID.randomUUID(), UUID.randomUUID(),
                    UUID.randomUUID(), BigDecimal.TEN, "VND", Instant.now())));
    }
}
