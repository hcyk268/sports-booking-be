package dev.booking.sports.identity.infrastructure.security;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import dev.booking.sports.identity.api.error.AuthErrorCode;
import dev.booking.sports.identity.infrastructure.redis.AuthStateStore;
import dev.booking.sports.shared.exception.ApiException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtTokenProvider tokenProvider;
	private final AuthStateStore authStateStore;
	private final HandlerExceptionResolver handlerExceptionResolver;

	public JwtAuthenticationFilter(
			JwtTokenProvider tokenProvider,
			AuthStateStore authStateStore,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {

		this.tokenProvider = tokenProvider;
		this.authStateStore = authStateStore;
		this.handlerExceptionResolver = handlerExceptionResolver;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {

		String token = bearerTokenOf(request);
		if (token == null) {
			filterChain.doFilter(request, response);
			return;
		}

		try {
			authenticate(token);
		}
		catch (ApiException exception) {
			SecurityContextHolder.clearContext();
			handlerExceptionResolver.resolveException(request, response, null, exception);
			return;
		}

		filterChain.doFilter(request, response);
	}

	private void authenticate(String token) {
		JwtTokenProvider.ParsedToken parsed = tokenProvider.parse(token, TokenType.ACCESS);
		AuthStateStore.AuthState state = authStateStore.read(parsed.userId(), parsed.jti());

		if (!state.tokenVersionExists()
				|| state.accessTokenBlacklisted()
				|| state.tokenVersion() != parsed.tokenVersion()) {
			throw new ApiException(AuthErrorCode.TOKEN_REVOKED);
		}

		AuthPrincipal principal = new AuthPrincipal(
				parsed.userId(),
				parsed.email(),
				parsed.jti(),
				parsed.roles(),
				parsed.permissions());

		UsernamePasswordAuthenticationToken authentication =
				UsernamePasswordAuthenticationToken.authenticated(principal, null, authoritiesOf(parsed));
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}

	private List<GrantedAuthority> authoritiesOf(JwtTokenProvider.ParsedToken parsed) {
		return Stream.concat(
				parsed.roles().stream().map(role -> "ROLE_" + role),
				parsed.permissions().stream())
				.map(authority -> (GrantedAuthority) new SimpleGrantedAuthority(authority))
				.toList();
	}

	private String bearerTokenOf(HttpServletRequest request) {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.startsWith(BEARER_PREFIX)) {
			return null;
		}
		String token = header.substring(BEARER_PREFIX.length()).trim();
		return token.isEmpty() ? null : token;
	}
}
