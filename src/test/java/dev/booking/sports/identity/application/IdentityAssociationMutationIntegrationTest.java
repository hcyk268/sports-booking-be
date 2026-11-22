package dev.booking.sports.identity.application;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import dev.booking.sports.identity.api.dto.request.ApplyRolePermissionsRequest;
import dev.booking.sports.identity.api.dto.request.ReplaceUserRolesRequest;
import dev.booking.sports.identity.api.error.AuthErrorCode;
import dev.booking.sports.identity.domain.model.Role;
import dev.booking.sports.identity.domain.model.User;
import dev.booking.sports.identity.domain.repository.PermissionRepository;
import dev.booking.sports.identity.domain.repository.RoleRepository;
import dev.booking.sports.identity.domain.repository.UserRepository;
import dev.booking.sports.identity.infrastructure.redis.RolePermissionChangePendingStore;
import dev.booking.sports.identity.infrastructure.redis.OtpPurpose;
import dev.booking.sports.identity.infrastructure.security.ApplicationUserDetailsService;
import dev.booking.sports.support.BaseIntegrationTest;
import dev.booking.sports.support.IntegrationTest;
import dev.booking.sports.shared.exception.ApiException;
import dev.booking.sports.shared.exception.ConcurrencyErrorCode;
import jakarta.persistence.OptimisticLockException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@IntegrationTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class IdentityAssociationMutationIntegrationTest extends BaseIntegrationTest {
    @Autowired private UserService users;
    @Autowired private RolePermissionChangeService rolePermissions;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PermissionRepository permissionRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private RolePermissionChangePendingStore pendingStore;
    @MockitoBean private OtpService otpService;
    @MockitoBean private AuthSessionService sessions;
    @MockitoBean private ApplicationUserDetailsService userDetails;

    private final UUID actorId = UUID.randomUUID();
    private UUID userId, roleId, otherRoleId;
    private String roleCode, otherRoleCode;
    private long userVersion, roleVersion, otherRoleVersion;
    private java.time.Instant roleAssignedAt, userAssignedAt;
    private TransactionTemplate transaction;

    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            Role role = createRole("USER_READ");
            Role other = createRole("ROLE_READ");
            roleId = role.getId();
            roleCode = role.getCode();
            otherRoleId = other.getId();
            otherRoleCode = other.getCode();
            roleVersion = role.getVersion();
            otherRoleVersion = other.getVersion();
            roleAssignedAt = role.getRolePermissions().iterator().next().getAssignedAt();
            User user = User.register(UUID.randomUUID() + "@example.com", "unused-test-hash", "Mutation test", null);
            user.assignRole(role);
            user = userRepository.saveAndFlush(user);
            userId = user.getId();
            userVersion = user.getVersion();
            userAssignedAt = user.getUserRoles().iterator().next().getAssignedAt();
        });
    }

    @AfterEach
    void tearDown() {
        transaction.executeWithoutResult(status -> {
            userRepository.deleteById(userId);
            userRepository.flush();
            roleRepository.deleteById(roleId);
            roleRepository.deleteById(otherRoleId);
            roleRepository.flush();
        });
    }

    @Test
    void changingRolePermissionsLocksOnlyRoleAndReturnsCommittedVersion() {
        Set<String> requested = Set.of("ROLE_READ");
        when(pendingStore.read(actorId, roleCode)).thenReturn(Optional.of(requested));

        var response = rolePermissions.applyChange(actorId, roleCode,
                new ApplyRolePermissionsRequest(roleVersion, requested, "123456"));

        assertThat(response.permissionCodes()).isEqualTo(requested);
        transaction.executeWithoutResult(status -> {
            Role persisted = roleRepository.findByCodeWithPermissions(roleCode).orElseThrow();
            assertThat(persisted.permissionCodes()).isEqualTo(requested);
            assertThat(persisted.getVersion()).isGreaterThan(roleVersion).isEqualTo(response.version());
            assertThat(roleRepository.findById(otherRoleId).orElseThrow().getVersion()).isEqualTo(otherRoleVersion);
            assertThat(userRepository.findById(userId).orElseThrow().getVersion()).isEqualTo(userVersion);
        });
    }

    @Test
    void replacingUserRolesDoesNotLockFetchedRolesOrPermissionsAndReturnsCommittedVersion() {
        var response = users.replaceRoles(actorId, userId,
                new ReplaceUserRolesRequest(userVersion, Set.of(otherRoleCode)));

        assertThat(response.roles()).containsExactly(otherRoleCode);
        assertThat(response.permissions()).containsExactly("ROLE_READ");
        transaction.executeWithoutResult(status -> {
            User persisted = userRepository.findByIdWithRolesAndPermissions(userId).orElseThrow();
            assertThat(persisted.roleCodes()).containsExactly(otherRoleCode);
            assertThat(persisted.getVersion()).isGreaterThan(userVersion).isEqualTo(response.version());
            assertThat(roleRepository.findById(roleId).orElseThrow().getVersion()).isEqualTo(roleVersion);
            assertThat(roleRepository.findById(otherRoleId).orElseThrow().getVersion()).isEqualTo(otherRoleVersion);
        });
    }

    @Test
    void concurrentPermissionChangesUsingTheSameVersionCannotBothCommit() throws Exception {
        Set<String> requested = Set.of("ROLE_READ");
        CountDownLatch bothPending = new CountDownLatch(2);
        CountDownLatch beginUpdates = new CountDownLatch(1);
        when(pendingStore.read(actorId, roleCode)).thenAnswer(call -> {
            bothPending.countDown();
            if (!beginUpdates.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out starting concurrent changes");
            return Optional.of(requested);
        });
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> rolePermissions.applyChange(actorId, roleCode,
                    new ApplyRolePermissionsRequest(roleVersion, requested, "123456")));
            var second = executor.submit(() -> rolePermissions.applyChange(actorId, roleCode,
                    new ApplyRolePermissionsRequest(roleVersion, requested, "123456")));
            assertThat(bothPending.await(10, TimeUnit.SECONDS)).isTrue();
            beginUpdates.countDown();
            int committed = 0;
            var failures = new ArrayList<Throwable>();
            for (var result : java.util.List.of(first, second)) {
                try {
                    result.get(15, TimeUnit.SECONDS);
                    committed++;
                } catch (ExecutionException failure) {
                    failures.add(failure.getCause());
                }
            }
            assertThat(committed).isEqualTo(1);
            assertThat(failures).singleElement().satisfies(failure -> {
                if (failure instanceof ApiException api)
                    assertThat(api.getErrorCode()).isEqualTo(ConcurrencyErrorCode.OPTIMISTIC_LOCK_CONFLICT);
                else assertThat(failure).isInstanceOfAny(OptimisticLockingFailureException.class, OptimisticLockException.class);
            });
            transaction.executeWithoutResult(status ->
                    assertThat(roleRepository.findByCodeWithPermissions(roleCode).orElseThrow().permissionCodes()).isEqualTo(requested));
        } finally {
            beginUpdates.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void invalidOtpRollsBackTheImmediateVersionIncrement() {
        Set<String> requested = Set.of("ROLE_READ");
        when(pendingStore.read(actorId, roleCode)).thenReturn(Optional.of(requested));
        doThrow(new ApiException(AuthErrorCode.OTP_INVALID)).when(otpService)
                .verify(OtpPurpose.ROLE_PERMISSION_CHANGE, actorId, roleCode, "bad-otp");

        assertThatThrownBy(() -> rolePermissions.applyChange(actorId, roleCode,
                new ApplyRolePermissionsRequest(roleVersion, requested, "bad-otp")))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(AuthErrorCode.OTP_INVALID));

        transaction.executeWithoutResult(status -> {
            Role persisted = roleRepository.findByCodeWithPermissions(roleCode).orElseThrow();
            assertThat(persisted.getVersion()).isEqualTo(roleVersion);
            assertThat(persisted.permissionCodes()).containsExactly("USER_READ");
        });
        verify(pendingStore, never()).delete(actorId, roleCode);
        verifyNoInteractions(sessions);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void replacingPermissionsCanKeepExistingPermissions(boolean addPermission) {
        Set<String> requested = addPermission ? Set.of("USER_READ", "ROLE_READ") : Set.of("USER_READ");
        when(pendingStore.read(actorId, roleCode)).thenReturn(Optional.of(requested));

        var response = rolePermissions.applyChange(actorId, roleCode,
                new ApplyRolePermissionsRequest(roleVersion, requested, "123456"));

        assertThat(response.permissionCodes()).isEqualTo(requested);
        transaction.executeWithoutResult(status -> {
            Role persisted = roleRepository.findByCodeWithPermissions(roleCode).orElseThrow();
            assertThat(persisted.permissionCodes()).isEqualTo(requested);
            assertThat(persisted.getVersion()).isGreaterThan(roleVersion).isEqualTo(response.version());
            assertThat(persisted.getRolePermissions().stream()
                    .filter(link -> link.getPermission().getCode().equals("USER_READ"))
                    .findFirst().orElseThrow().getAssignedAt())
                    .isCloseTo(roleAssignedAt, within(1, java.time.temporal.ChronoUnit.MICROS));
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void replacingRolesCanKeepExistingRoles(boolean addRole) {
        Set<String> requested = addRole ? Set.of(roleCode, otherRoleCode) : Set.of(roleCode);

        var response = users.replaceRoles(actorId, userId, new ReplaceUserRolesRequest(userVersion, requested));

        assertThat(response.roles()).isEqualTo(requested);
        transaction.executeWithoutResult(status -> {
            User persisted = userRepository.findByIdWithRolesAndPermissions(userId).orElseThrow();
            assertThat(persisted.roleCodes()).isEqualTo(requested);
            assertThat(persisted.getVersion()).isGreaterThan(userVersion).isEqualTo(response.version());
            assertThat(persisted.getUserRoles().stream()
                    .filter(link -> link.getRole().getId().equals(roleId))
                    .findFirst().orElseThrow().getAssignedAt())
                    .isCloseTo(userAssignedAt, within(1, java.time.temporal.ChronoUnit.MICROS));
            assertThat(roleRepository.findById(roleId).orElseThrow().getVersion()).isEqualTo(roleVersion);
            assertThat(roleRepository.findById(otherRoleId).orElseThrow().getVersion()).isEqualTo(otherRoleVersion);
        });
    }

    private Role createRole(String permissionCode) {
        Role role = new Role();
        role.setCode("TEST_" + UUID.randomUUID());
        role.setName("Mutation test role");
        roleRepository.saveAndFlush(role);
        role.replacePermissions(permissionRepository.findByCodeIn(Set.of(permissionCode)));
        return roleRepository.saveAndFlush(role);
    }
}
