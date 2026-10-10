package dev.booking.sports.notification.email;

import java.io.IOException;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClients;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.sendgrid.Client;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Personalization;

import dev.booking.sports.notification.config.SendGridProperties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SendGridEmailSender implements EmailSender {

	private static final String MAIL_SEND_ENDPOINT = "mail/send";
	private static final int CONNECT_TIMEOUT_MS = 5_000;
	private static final int SOCKET_TIMEOUT_MS = 10_000;

	private final SendGrid sendGrid;
	private final Email from;

	public SendGridEmailSender(SendGridProperties properties) {
		Assert.hasText(properties.apiKey(), "Send Grid API Key is required");
		Assert.hasText(properties.fromEmail(), "Send Grid From Mail is required");

		this.sendGrid = new SendGrid(properties.apiKey(), timeoutBoundClient());
		this.from = new Email(properties.fromEmail(), properties.fromName());
	}

	@Override
	public void send(EmailMessage message) {
		Request request = new Request();
		request.setMethod(Method.POST);
		request.setEndpoint(MAIL_SEND_ENDPOINT);

		try {
			request.setBody(buildMail(message).build());
			Response response = sendGrid.api(request);

			if (response.getStatusCode() < 200 || response.getStatusCode() > 299) {
				throw new EmailDeliveryException("SendGrid rejected the message with status %d: %s"
						.formatted(response.getStatusCode(), response.getBody()));
			}
		}
		catch (IOException exception) {
			throw new EmailDeliveryException("Could not reach SendGrid", exception);
		}
	}

	private Mail buildMail(EmailMessage message) {
		Personalization personalization = new Personalization();
		personalization.addTo(new Email(message.to(), message.toName()));

		Mail mail = new Mail();
		mail.setFrom(from);
		mail.setSubject(message.subject());
		mail.addPersonalization(personalization);
		mail.addContent(new Content("text/html", message.htmlBody()));
		return mail;
	}

	private Client timeoutBoundClient() {
		RequestConfig requestConfig = RequestConfig.custom()
				.setConnectTimeout(CONNECT_TIMEOUT_MS)
				.setConnectionRequestTimeout(CONNECT_TIMEOUT_MS)
				.setSocketTimeout(SOCKET_TIMEOUT_MS)
				.build();

		return new Client(HttpClients.custom().setDefaultRequestConfig(requestConfig).build());
	}
}
