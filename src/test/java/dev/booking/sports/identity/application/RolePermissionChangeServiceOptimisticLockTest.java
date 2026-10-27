package dev.booking.sports.identity.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.booking.sports.identity.api.dto.request.ReplaceRolePermissionsRequest;
import dev.booking.sports.identity.domain.model.Permission;
import dev.booking.sports.identity.domain.model.Role;
import dev.booking.sports.identity.domain.repository.PermissionRepository;
import dev.booking.sports.identity.domain.repository.RoleRepository;
import dev.booking.sports.identity.domain.repository.UserRepository;
import dev.booking.sports.identity.infrastructure.redis.RolePermissionChangePendingStore;
import dev.booking.sports.identity.infrastructure.security.ApplicationUserDetailsService;
import dev.booking.sports.shared.exception.ApiException;
import dev.booking.sports.shared.exception.ConcurrencyErrorCode;
import dev.booking.sports.shared.outbox.OutboxService;

@ExtendWith(MockitoExtension.class)
class RolePermissionChangeServiceOptimisticLockTest {

    private static final UUID ACTOR_ID =
            UUID.fromString("67676767-6767-6767-6767-676767676767");

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RolePermissionChangePolicy policy;

    @Mock
    private RolePermissionChangePendingStore pendingStore;

    @Mock
    private OtpService otpService;

    @Mock
    private OutboxService outboxService;

    @Mock
    private AuthSessionService sessionService;

    @Mock
    private ApplicationUserDetailsService userDetailsService;

    @InjectMocks
    private RolePermissionChangeService service;

    @Test
    void requestChange_withStaleRoleVersion_doesNotCreatePendingChange() {
        Set<String> permissionCodes = Set.of("USER_READ");
        Permission permission = new Permission();
        permission.setCode("USER_READ");
        Role role = new Role();
        role.setCode("PROVIDER");
        role.setVersion(4L);

        when(policy.normalizePermissionCodes(permissionCodes))
                .thenReturn(permissionCodes);
        when(permissionRepository.findByCodeIn(permissionCodes))
                .thenReturn(Set.of(permission));
        when(roleRepository.findByCode("PROVIDER"))
                .thenReturn(Optional.of(role));

        ReplaceRolePermissionsRequest request =
                new ReplaceRolePermissionsRequest(3L, permissionCodes);

        assertThatThrownBy(() ->
                service.requestChange(ACTOR_ID, "PROVIDER", request))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ConcurrencyErrorCode.OPTIMISTIC_LOCK_CONFLICT);

		verifyNoInteractions(pendingStore);
    }
}
