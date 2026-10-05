package dev.booking.sports.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.web.util.UriComponentsBuilder;

@ConfigurationProperties(prefix = "app.frontend")
public record FrontendProperties(String baseUrl, String verifyEmailPath) {

	public String verifyEmailUrl(String token) {
		return UriComponentsBuilder.fromUriString(baseUrl)
				.path(verifyEmailPath)
				.queryParam("token", token)
				.build()
				.toUriString();
	}
}
