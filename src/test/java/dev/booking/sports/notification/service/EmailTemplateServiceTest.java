package dev.booking.sports.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import dev.booking.sports.notification.service.EmailTemplateService.RenderedEmail;

class EmailTemplateServiceTest {

	private EmailTemplateService emailTemplateService;

	@BeforeEach
	void setUp() {
		ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
		resolver.setPrefix("templates/");
		resolver.setSuffix(".html");
		resolver.setTemplateMode(TemplateMode.HTML);
		resolver.setCharacterEncoding("UTF-8");

		SpringTemplateEngine engine = new SpringTemplateEngine();
		engine.setTemplateResolver(resolver);
		emailTemplateService = new EmailTemplateService(engine);
	}

	@Test
	void verification_rendersSubjectAndBody() {
		RenderedEmail email = emailTemplateService.verification("Minh Anh", "https://app.example/verify?token=abc");

		assertThat(email.subject()).isEqualTo("Kích hoạt tài khoản Sports Booking");
		assertThat(email.htmlBody()).contains("Minh Anh");
		assertThat(email.htmlBody()).contains("https://app.example/verify?token=abc");
	}

	@Test
	void passwordReset_rendersOtpAndTtl() {
		RenderedEmail email = emailTemplateService.passwordReset("Minh Anh", "123456", Duration.ofMinutes(10));

		assertThat(email.subject()).isEqualTo("Mã xác thực đặt lại mật khẩu");
		assertThat(email.htmlBody()).contains("123456");
		assertThat(email.htmlBody()).contains("10");
	}

	@Test
	void passwordChanged_rendersRecipientName() {
		RenderedEmail email = emailTemplateService.passwordChanged("Minh Anh");

		assertThat(email.subject()).isEqualTo("Mật khẩu của bạn đã được thay đổi");
		assertThat(email.htmlBody()).contains("Minh Anh");
	}
}
