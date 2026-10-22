package dev.booking.sports.identity.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import dev.booking.sports.identity.domain.model.User;
import dev.booking.sports.support.BaseIntegrationTest;
import dev.booking.sports.support.IntegrationTest;

@IntegrationTest
class IdentityOptimisticLockIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

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
