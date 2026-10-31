package dev.booking.sports.payment.api.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import dev.booking.sports.identity.infrastructure.security.AuthPrincipal;
import dev.booking.sports.payment.api.dto.response.PaymentCheckoutResponse;
import dev.booking.sports.payment.application.PaymentQueryService;
import dev.booking.sports.payment.application.PaymentService;
import dev.booking.sports.payment.domain.enums.PaymentStatus;
import dev.booking.sports.shared.exception.GlobalExceptionHandler;

class PaymentControllerTest {
    @Test
    void resumeUsesAuthenticatedCustomerAndNeedsNeitherBodyNorIdempotencyKey() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        var queryService = mock(PaymentQueryService.class);
        var paymentService = mock(PaymentService.class);
        when(paymentService.resumePayment(customerId, paymentId)).thenReturn(new PaymentCheckoutResponse(
                paymentId, attemptId, PaymentStatus.PENDING, new BigDecimal("300000"), "VND", Instant.now().plusSeconds(600),
                new PaymentCheckoutResponse.CheckoutForm("https://pay-sandbox.sepay.vn/v1/checkout/init", "POST",
                        List.of(new PaymentCheckoutResponse.Field("signature", "original-signature")))));
        var mvc = MockMvcBuilders.standaloneSetup(new PaymentController(queryService, paymentService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthPrincipal(customerId, "customer@test.local", "token", Set.of("CUSTOMER"), Set.of()),
                null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
        SecurityContextHolder.setContext(context);
        String path = "/api/v1/payments/" + paymentId + "/checkout";
        try {
            mvc.perform(post(path))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.paymentId").value(paymentId.toString()))
                    .andExpect(jsonPath("$.data.attemptId").value(attemptId.toString()))
                    .andExpect(jsonPath("$.data.checkout.fields[0].value").value("original-signature"));
            verify(paymentService).resumePayment(customerId, paymentId);
            verifyNoMoreInteractions(paymentService);
            verifyNoInteractions(queryService);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
