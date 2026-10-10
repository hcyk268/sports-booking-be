package dev.booking.sports.identity.infrastructure.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import dev.booking.sports.identity.api.error.AuthErrorCode;
import dev.booking.sports.shared.exception.ApiException;


@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	private static final String[] PUBLIC_AUTH_ENDPOINTS = {
			"/api/v1/auth/register",
			"/api/v1/auth/verify-email",
			"/api/v1/auth/resend-verification",
			"/api/v1/auth/login",
			"/api/v1/auth/refresh",
			"/api/v1/auth/forgot-password",
			"/api/v1/auth/reset-password"
	};

	private static final String[] PUBLIC_DOCS_ENDPOINTS = {
			"/v3/api-docs/**",
			"/swagger-ui/**",
			"/swagger-ui.html",
			"/actuator/health",
			"/actuator/info"
	};

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(5);
	}

	@Bean
	public DaoAuthenticationProvider daoAuthenticationProvider(
			UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {

		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return provider;
	}

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
		return config.getAuthenticationManager();
	}

	@Bean
	public SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			JwtAuthenticationFilter jwtAuthenticationFilter,
			AuthenticationEntryPoint authenticationEntryPoint,
			AccessDeniedHandler accessDeniedHandler) throws Exception {

		return http
				.csrf(csrf -> csrf.disable())
				.httpBasic(basic -> basic.disable())
				.formLogin(form -> form.disable())
				.logout(logout -> logout.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(requests -> requests
						.requestMatchers(PUBLIC_AUTH_ENDPOINTS).permitAll()
						.requestMatchers(PUBLIC_DOCS_ENDPOINTS).permitAll()
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(handling -> handling
						.authenticationEntryPoint(authenticationEntryPoint)
						.accessDeniedHandler(accessDeniedHandler))
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
				.build();
	}

	
	@Bean
	public AuthenticationEntryPoint authenticationEntryPoint(
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {

		return (request, response, authException) -> resolver.resolveException(
				request, response, null, new ApiException(AuthErrorCode.AUTHENTICATION_REQUIRED));
	}

	@Bean
	public AccessDeniedHandler accessDeniedHandler(
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {

		return (request, response, deniedException) -> resolver.resolveException(
				request, response, null, new ApiException(AuthErrorCode.ACCESS_DENIED));
	}
}
