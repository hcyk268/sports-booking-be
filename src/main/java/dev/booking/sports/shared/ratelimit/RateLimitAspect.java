package dev.booking.sports.shared.ratelimit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import dev.booking.sports.shared.exception.ApiException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class RateLimitAspect {

	private final FixedWindowRateLimiter rateLimiter;

	@Around("@annotation(rateLimit)")
	public Object enforce(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
		validate(rateLimit);

		if (!rateLimiter.tryConsume(
				rateLimit.action(),
				clientIp(),
				rateLimit.maxRequests(),
				rateLimit.timeWindow())) {

			throw new ApiException(RateLimitErrorCode.TOO_MANY_REQUESTS);
		}

		return joinPoint.proceed();
	}

	private String clientIp() {
		HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
				.getRequest();
		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (forwardedFor != null && !forwardedFor.isBlank()) {
			return forwardedFor.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}

	private void validate(RateLimit rateLimit) {
		if (rateLimit.action().isBlank()) {
			throw new IllegalStateException("@RateLimit action must not be blank");
		}
		if (rateLimit.maxRequests() <= 0 || rateLimit.timeWindow() <= 0) {
			throw new IllegalStateException("@RateLimit limits must be greater than zero");
		}
	}
}
