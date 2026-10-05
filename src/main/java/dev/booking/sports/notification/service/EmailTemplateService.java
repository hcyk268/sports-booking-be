package dev.booking.sports.notification.service;

import java.time.Duration;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@RequiredArgsConstructor
@Service
public class EmailTemplateService {

	private static final String VERIFICATION_SUBJECT = "Kích hoạt tài khoản Sports Booking";
	private static final String PASSWORD_RESET_SUBJECT = "Mã xác thực đặt lại mật khẩu";
	private static final String PASSWORD_CHANGE_SUBJECT = "Mã xác thực đổi mật khẩu";
	private static final String PASSWORD_CHANGED_SUBJECT = "Mật khẩu của bạn đã được thay đổi";

	private final TemplateEngine templateEngine;

	public RenderedEmail verification(String fullName, String verificationUrl) {
		return new RenderedEmail(
				VERIFICATION_SUBJECT,
				render("email/verification", Map.of(
						"fullName", fullName,
						"verificationUrl", verificationUrl)));
	}

	public RenderedEmail passwordReset(String fullName, String otp, Duration ttl) {
		return new RenderedEmail(
				PASSWORD_RESET_SUBJECT,
				render("email/password-reset", Map.of(
						"fullName", fullName,
						"otp", otp,
						"ttlMinutes", ttl.toMinutes())));
	}

	public RenderedEmail passwordChangeOtp(String fullName, String otp, Duration ttl) {
		return new RenderedEmail(
				PASSWORD_CHANGE_SUBJECT,
				render("email/password-change-otp", Map.of(
						"fullName", fullName,
						"otp", otp,
						"ttlMinutes", ttl.toMinutes())));
	}

	public RenderedEmail passwordChanged(String fullName) {
		return new RenderedEmail(
				PASSWORD_CHANGED_SUBJECT,
				render("email/password-changed", Map.of("fullName", fullName)));
	}

	private String render(String templateName, Map<String, Object> variables) {
		Context context = new Context();
		context.setVariables(variables);
		return templateEngine.process(templateName, context);
	}

	public record RenderedEmail(String subject, String htmlBody) {
	}
}
