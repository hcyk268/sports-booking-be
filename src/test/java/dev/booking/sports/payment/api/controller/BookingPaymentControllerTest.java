package dev.booking.sports.payment.api.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import dev.booking.sports.payment.application.PaymentQueryService;
import dev.booking.sports.payment.application.BookingPaymentService;
import dev.booking.sports.shared.exception.GlobalExceptionHandler;

class BookingPaymentControllerTest {
    @Test
    void missingIdempotencyKeyReturns400WithoutCallingPaymentServices() throws Exception {
        var creation = mock(BookingPaymentService.class);
        var queries = mock(PaymentQueryService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new BookingPaymentController(creation, queries))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        String path = "/api/v1/bookings/" + UUID.randomUUID() + "/payments";

        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("MISSING_HEADER"))
                .andExpect(jsonPath("$.message").value("Required header 'Idempotency-Key' is missing"))
                .andExpect(jsonPath("$.path").value(path));

        verifyNoInteractions(creation, queries);
    }
}
