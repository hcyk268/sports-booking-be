package dev.booking.sports.booking.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.booking.hold")
public record BookingHoldProperties(
        Duration duration,
        Duration lockTtl) {

    public BookingHoldProperties {
        duration = duration == null ? Duration.ofMinutes(10) : duration;
        lockTtl = lockTtl == null ? Duration.ofSeconds(30) : lockTtl;
        if (duration.isNegative() || duration.isZero()
                || lockTtl.isNegative() || lockTtl.isZero()) {
            throw new IllegalArgumentException("Booking hold duration and lock TTL must be positive");
        }
    }
}
