package dev.booking.sports.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import dev.booking.sports.booking.domain.event.BookingSlotSnapshot;
import dev.booking.sports.notification.email.BookingEmailDetails;

class BookingEmailTemplateServiceTest {
    private BookingEmailTemplateService templates() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return new BookingEmailTemplateService(engine, ZoneId.of("Asia/Ho_Chi_Minh"));
    }

    @Test void rendersAmountLocalSlotTimesAndEscapesUntrustedText() {
        var slot = new BookingSlotSnapshot(UUID.randomUUID(), UUID.randomUUID(),
                Instant.parse("2026-11-13T03:00:00Z"), Instant.parse("2026-11-13T04:00:00Z"), new BigDecimal("300000"));
        var details = new BookingEmailDetails("BK-001", new BigDecimal("300000"), "VND", List.of(slot), "<script>alert(1)</script>");
        var email = templates().render("<b>Customer</b>", "Đặt sân đã bị hủy", "Đơn đặt sân đã bị hủy.", details);
        assertThat(email.subject()).isEqualTo("Đặt sân đã bị hủy – BK-001");
        assertThat(email.htmlBody()).contains("300.000 VND", "13/11/2026 10:00", "13/11/2026 11:00", "Asia/Ho_Chi_Minh")
                .contains("&lt;b&gt;Customer&lt;/b&gt;", "&lt;script&gt;")
                .doesNotContain("<script>", "<b>Customer</b>");
    }

    @Test void omitsCancellationReasonAndSlotsWhenAbsent() {
        var email = templates().render("Customer", "Đặt sân thành công", "Đã xác nhận.",
                new BookingEmailDetails("BK-002", new BigDecimal("300000"), "VND", List.of(), null));
        assertThat(email.htmlBody()).contains("BK-002", "Đã xác nhận.").doesNotContain("Lý do hủy:", "Khung giờ (");
    }
}
