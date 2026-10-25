package dev.booking.sports.booking.infrastructure.scheduling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import dev.booking.sports.booking.application.BookingExpiryService;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.booking.hold",
        name = "expiry-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class BookingExpirySchedule {

    private final BookingExpiryService expiryService;

    @Scheduled(fixedDelayString = "${app.booking.hold.expiry-poll-interval:15s}")
    public void expireHolds() {
        expiryService.expireBatch(25);
    }
}
