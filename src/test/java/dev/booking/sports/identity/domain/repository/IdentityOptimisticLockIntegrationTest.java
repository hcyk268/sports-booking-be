package dev.booking.sports.identity.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import dev.booking.sports.identity.domain.model.User;
import dev.booking.sports.identity.api.dto.request.ApplyRolePermissionsRequest;
import dev.booking.sports.identity.api.dto.request.ReplaceRolePermissionsRequest;
import dev.booking.sports.identity.api.dto.request.ReplaceUserRolesRequest;
import dev.booking.sports.identity.application.RolePermissionChangeService;
import dev.booking.sports.identity.application.UserService;
import dev.booking.sports.support.BaseIntegrationTest;
import dev.booking.sports.support.IntegrationTest;
import jakarta.persistence.EntityManager;

@IntegrationTest
@TestPropertySource(properties = "app.outbox.relay-enabled=false")
class IdentityOptimisticLockIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private RolePermissionChangeService rolePermissionChangeService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private UserService userService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        User user = User.register(
                UUID.randomUUID() + "@example.com",
                "unused-test-hash",
                "Original user",
                null);
        userId = userRepository.saveAndFlush(user).getId();
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(userId);
    }

    @Test
    void otpCanUpdateSeededRoleWithExistingPermissionsAndReturnsPersistedVersion() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            status.setRollbackOnly();
            var original = roleRepository.findByCodeWithPermissions("PROVIDER").orElseThrow();
            var version = original.getVersion();
            var updatedCodes = new HashSet<>(original.permissionCodes());
            assertThat(updatedCodes.add("USER_READ")).isTrue();
            entityManager.clear();

            rolePermissionChangeService.requestChange(
                    userId, "PROVIDER", new ReplaceRolePermissionsRequest(version, updatedCodes));
            entityManager.flush();
            String otp = jdbc.queryForObject("""
                    SELECT payload->>'otp' FROM outbox_events
                    WHERE aggregate_id = ? AND event_type = 'identity.role-permission-change-otp-issued'
                    """, String.class, userId.toString());
            var response = rolePermissionChangeService.applyChange(
                    userId, "PROVIDER", new ApplyRolePermissionsRequest(version, updatedCodes, otp));

            entityManager.flush();
            entityManager.clear();
            var saved = roleRepository.findByCodeWithPermissions("PROVIDER").orElseThrow();
            assertThat(response.version()).isEqualTo(version + 1).isEqualTo(saved.getVersion());
            assertThat(response.permissionCodes()).containsExactlyInAnyOrderElementsOf(updatedCodes);
            assertThat(saved.permissionCodes()).containsExactlyInAnyOrderElementsOf(updatedCodes);
        });
    }

    @Test
    void userRoleReplacementKeepsExistingAssignmentAndReturnsPersistedVersion() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            status.setRollbackOnly();
            var user = userRepository.findById(userId).orElseThrow();
            user.assignRole(roleRepository.findByCode("CUSTOMER").orElseThrow());
            entityManager.flush();
            var version = user.getVersion();
            entityManager.clear();

            var response = userService.replaceRoles(
                    UUID.randomUUID(), userId,
                    new ReplaceUserRolesRequest(version, Set.of("CUSTOMER", "PROVIDER")));

            entityManager.flush();
            entityManager.clear();
            var saved = userRepository.findByIdWithRolesAndPermissions(userId).orElseThrow();
            assertThat(response.version()).isEqualTo(version + 1).isEqualTo(saved.getVersion());
            assertThat(saved.roleCodes()).containsExactlyInAnyOrder("CUSTOMER", "PROVIDER");
        });
    }

    @Test
    void concurrentTransactionsUpdatingSameUserVersion_onlyOneSucceeds()
            throws Exception {

        CountDownLatch bothLoaded = new CountDownLatch(2);
        CountDownLatch beginUpdates = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Long> first = executor.submit(
                    () -> updateInTransaction("First update", bothLoaded, beginUpdates));
            Future<Long> second = executor.submit(
                    () -> updateInTransaction("Second update", bothLoaded, beginUpdates));

            assertThat(bothLoaded.await(10, TimeUnit.SECONDS)).isTrue();
            beginUpdates.countDown();

            List<Long> successfulVersions = new ArrayList<>();
            List<Throwable> failures = new ArrayList<>();
            collect(first, successfulVersions, failures);
            collect(second, successfulVersions, failures);

            assertThat(successfulVersions).containsExactly(1L);
            assertThat(failures).singleElement()
                    .isInstanceOf(OptimisticLockingFailureException.class);

            User persisted = userRepository.findById(userId).orElseThrow();
            assertThat(persisted.getVersion()).isEqualTo(1L);
            assertThat(persisted.getFullName())
                    .isIn("First update", "Second update");
        } finally {
            beginUpdates.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Long updateInTransaction(
            String fullName,
            CountDownLatch bothLoaded,
            CountDownLatch beginUpdates) {

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status -> {
            User user = userRepository.findById(userId).orElseThrow();
            assertThat(user.getVersion()).isZero();
            bothLoaded.countDown();
            await(beginUpdates);

            user.setFullName(fullName);
            userRepository.saveAndFlush(user);
            return user.getVersion();
        });
    }

    private void collect(
            Future<Long> result,
            List<Long> successfulVersions,
            List<Throwable> failures) throws Exception {

        try {
            successfulVersions.add(result.get(10, TimeUnit.SECONDS));
        } catch (ExecutionException exception) {
            failures.add(exception.getCause());
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent update");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent update was interrupted", exception);
        }
    }
}
