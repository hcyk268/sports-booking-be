package dev.booking.sports.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import dev.booking.sports.identity.api.dto.request.UpdateUserProfileRequest;
import dev.booking.sports.identity.config.FrontendProperties;
import dev.booking.sports.identity.domain.model.User;
import dev.booking.sports.identity.domain.repository.RoleRepository;
import dev.booking.sports.identity.domain.repository.UserRepository;
import dev.booking.sports.identity.infrastructure.security.ApplicationUserDetailsService;
import dev.booking.sports.shared.exception.ApiException;
import dev.booking.sports.shared.exception.ConcurrencyErrorCode;
import dev.booking.sports.shared.outbox.OutboxService;

@ExtendWith(MockitoExtension.class)
class UserServiceOptimisticLockTest {

    private static final UUID USER_ID =
            UUID.fromString("56565656-5656-5656-5656-565656565656");

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationUserDetailsService userDetailsService;

    @Mock
    private AuthSessionService sessionService;

    @Mock
    private OutboxService outboxService;

    @Mock
    private FrontendProperties frontendProperties;

    @InjectMocks
    private UserService userService;

    @Test
    void updateMe_withStaleVersion_rejectsBeforeMutation() {
        User user = userWithVersion(2L);
        when(userRepository.findByIdWithRolesAndPermissions(USER_ID))
                .thenReturn(Optional.of(user));

        UpdateUserProfileRequest request =
                new UpdateUserProfileRequest(1L, "New name", null);

        assertThatThrownBy(() -> userService.updateMe(USER_ID, request))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ConcurrencyErrorCode.OPTIMISTIC_LOCK_CONFLICT);

        assertThat(user.getFullName()).isEqualTo("Original name");
        verify(userRepository, never()).saveAndFlush(user);
        verify(userDetailsService, never()).evict(user.getEmail());
    }

    @Test
    void updateMe_flushesBeforeCacheEvictionAndReturnsNewVersion() {
        User user = userWithVersion(2L);
        when(userRepository.findByIdWithRolesAndPermissions(USER_ID))
                .thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(user)).thenAnswer(invocation -> {
            user.setVersion(3L);
            return user;
        });

        var response = userService.updateMe(
                USER_ID,
                new UpdateUserProfileRequest(2L, "New name", null));

        assertThat(response.version()).isEqualTo(3L);
        assertThat(response.fullName()).isEqualTo("New name");
        InOrder order = inOrder(userRepository, userDetailsService);
        order.verify(userRepository).saveAndFlush(user);
        order.verify(userDetailsService).evict(user.getEmail());
    }

    private User userWithVersion(Long version) {
        User user = User.register(
                "user@example.com", "hash", "Original name", null);
        user.setId(USER_ID);
        user.setVersion(version);
        return user;
    }
}
