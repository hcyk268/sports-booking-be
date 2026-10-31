package dev.booking.sports.payment.api.controller;

import static dev.booking.sports.payment.SePayIpnFixtures.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import dev.booking.sports.payment.application.SePayIpnService;
import dev.booking.sports.payment.config.SePayProperties;
import dev.booking.sports.payment.domain.enums.PaymentEnvironment;
import dev.booking.sports.payment.domain.repository.PaymentInboxRepository;
import dev.booking.sports.shared.exception.GlobalExceptionHandler;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import tools.jackson.databind.ObjectMapper;

class SePayIpnControllerTest {
    private static final String PATH = "/api/v1/payments/sepay/ipn";
    private final PaymentInboxRepository repository = mock(PaymentInboxRepository.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final TransactionStatus transaction = mock(TransactionStatus.class);
    private ValidatorFactory validators;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        validators = Validation.buildDefaultValidatorFactory();
        var service = new SePayIpnService(new SePayProperties(true, PaymentEnvironment.SANDBOX,
                "merchant", SECRET, null, null, null), new ObjectMapper(), validators.getValidator(), repository);
        when(transactions.getTransaction(any())).thenReturn(transaction);
        when(repository.findByProviderAndEnvironmentAndMerchantIdAndDeduplicationKey(any(), any(), any(), any()))
                .thenReturn(java.util.Optional.empty());
        var proxy = new ProxyFactory(service);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        mvc = MockMvcBuilders.standaloneSetup(new SePayIpnController((SePayIpnService) proxy.getProxy()))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void tearDown() { validators.close(); }

    @Test
    void returnsBareAcknowledgementOnlyAfterCommitWithoutUserPrincipal() throws Exception {
        mvc.perform(post(PATH).header("X-Secret-Key", SECRET).contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isOk()).andExpect(content().json("{\"success\":true}"));
        var order = inOrder(repository, transactions);
        order.verify(transactions).getTransaction(any());
        order.verify(repository).save(any());
        order.verify(transactions).commit(transaction);
    }

    @Test
    void failedCommitDoesNotReturnSuccess() throws Exception {
        doThrow(new IllegalStateException("Commit failed")).when(transactions).commit(transaction);
        mvc.perform(post(PATH).header("X-Secret-Key", SECRET).contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.success").doesNotExist());
    }

    @Test
    void missingSecretReturns401AndNeverWrites() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SEPAY_IPN_UNAUTHORIZED"));
        verifyNoInteractions(repository);
    }

    @Test
    void authenticatedMalformedPayloadReturns400() throws Exception {
        mvc.perform(post(PATH).header("X-Secret-Key", SECRET).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SEPAY_IPN_INVALID"));
        verifyNoInteractions(repository);
    }
}
