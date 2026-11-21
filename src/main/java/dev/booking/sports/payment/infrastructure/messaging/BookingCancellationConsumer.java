package dev.booking.sports.payment.infrastructure.messaging;

import java.io.IOException;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;
import dev.booking.sports.payment.application.BookingPaymentCancellationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingCancellationConsumer {

    private final ObjectMapper objectMapper;
    private final BookingPaymentCancellationService service;
    private final BookingCancellationRecovery recovery;

    @RabbitListener(id = "bookingPaymentCancellationConsumer", queues = BookingCancellationRabbitConfig.QUEUE,
            messageConverter = "rawPaymentCancellationMessageConverter", ackMode = "MANUAL")
    public void consume(Message raw, Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            var message = objectMapper.readValue(raw.getBody(), BookingCancellationMessage.class);
            service.cancelPendingPayment(message.payload());
        } catch (RuntimeException failure) {
            try {
                recovery.recover(raw, failure);
            } catch (RuntimeException recoveryFailure) {
                log.error("Could not confirm payment cancellation recovery; retaining original delivery", recoveryFailure);
                try { Thread.sleep(1_000); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                channel.basicNack(deliveryTag, false, true);
                return;
            }
        }
        // The transactional service has committed, or retry/DLQ publication was confirmed.
        channel.basicAck(deliveryTag, false);
    }
}
