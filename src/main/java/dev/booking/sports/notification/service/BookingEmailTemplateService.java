package dev.booking.sports.notification.service;

import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import dev.booking.sports.notification.email.BookingEmailDetails;
import dev.booking.sports.notification.service.EmailTemplateService.RenderedEmail;

@Service
public class BookingEmailTemplateService {
    private final TemplateEngine engine;
    private final ZoneId zoneId;

    public BookingEmailTemplateService(TemplateEngine engine,
            @Value("${app.booking.zone-id:Asia/Ho_Chi_Minh}") ZoneId zoneId) {
        this.engine = engine;
        this.zoneId = zoneId;
    }

    public RenderedEmail render(String fullName, String title, String body, BookingEmailDetails details) {
        var formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(zoneId);
        var context = new Context();
        context.setVariables(Map.of(
                "fullName", fullName, "title", title, "body", body, "bookingCode", details.bookingCode(),
                "amount", NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")).format(details.totalAmount()),
                "currency", details.currency(), "zoneId", zoneId.getId(),
                "slots", details.slots().stream().map(slot ->
                        formatter.format(slot.startAt()) + " – " + formatter.format(slot.endAt())).toList(),
                "cancellationReason", details.cancellationReason() == null ? "" : details.cancellationReason()));
        return new RenderedEmail(title + " – " + details.bookingCode(), engine.process("email/booking", context));
    }
}
