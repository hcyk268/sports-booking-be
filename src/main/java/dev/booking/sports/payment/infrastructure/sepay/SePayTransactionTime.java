package dev.booking.sports.payment.infrastructure.sepay;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;

public final class SePayTransactionTime {
    private static final DateTimeFormatter FORMAT = new DateTimeFormatterBuilder()
            .appendPattern("uuuu-MM-dd HH:mm:ss")
            .toFormatter()
            .withResolverStyle(ResolverStyle.STRICT);

    private SePayTransactionTime() {}

    public static Instant parse(String transactionDate, ZoneId zone) {
        if (transactionDate == null) return null;
        return LocalDateTime.parse(transactionDate, FORMAT).atZone(zone).toInstant();
    }
}
