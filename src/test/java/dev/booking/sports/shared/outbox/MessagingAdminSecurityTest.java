package dev.booking.sports.shared.outbox;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import dev.booking.sports.notification.service.EmailRecoveryController;
import dev.booking.sports.notification.service.EmailRecoveryService;

class MessagingAdminSecurityTest {
    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean OutboxRepository repository() { return mock(OutboxRepository.class); }
        @Bean OutboxAdminController controller(OutboxRepository repo) { return new OutboxAdminController(repo); }
        @Bean EmailRecoveryService recovery() { return mock(EmailRecoveryService.class); }
        @Bean EmailRecoveryController emails(EmailRecoveryService service) { return new EmailRecoveryController(service); }
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "user", "", AuthorityUtils.createAuthorityList(role)));
    }

    @Test void customerCannotReadOrReplayOutboxOrReplayEmails() {
        authenticate("ROLE_CUSTOMER");
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var controller = context.getBean(OutboxAdminController.class);
            assertThatThrownBy(controller::stats).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.replay(UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> context.getBean(EmailRecoveryController.class).replay(25))
                    .isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(context.getBean(OutboxRepository.class), context.getBean(EmailRecoveryService.class));
        }
    }

    @Test void adminCanReplayOnlyFailedEventsAndBoundedEmailBatch() {
        authenticate("ROLE_ADMIN");
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var repo = context.getBean(OutboxRepository.class);
            var controller = context.getBean(OutboxAdminController.class);
            UUID id = UUID.randomUUID();
            when(repo.replayFailed(id)).thenReturn(1, 0);
            assertThat(controller.replay(id).getStatusCode().value()).isEqualTo(202);
            assertThat(controller.replay(id).getStatusCode().value()).isEqualTo(409);
            var emails = context.getBean(EmailRecoveryController.class);
            assertThat(emails.replay(101).getStatusCode().value()).isEqualTo(400);
            assertThat(controller.failed(-1).getStatusCode().value()).isEqualTo(400);
            when(context.getBean(EmailRecoveryService.class).replayDeadLetters(25)).thenReturn(2);
            assertThat(emails.replay(25).getBody()).containsEntry("replayed", 2);
        }
    }
}
