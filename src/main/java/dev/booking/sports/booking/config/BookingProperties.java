package dev.booking.sports.booking.config;

import java.time.ZoneId;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.booking")
public record BookingProperties(ZoneId zoneId) {

    private static final ZoneId DEFAULT_ZONE_ID =
            ZoneId.of("Asia/Ho_Chi_Minh");

    public BookingProperties {
        zoneId = Objects.requireNonNullElse(zoneId, DEFAULT_ZONE_ID);
    }
}
