package dev.booking.sports.court.domain.repository;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import dev.booking.sports.court.domain.model.Court;
import dev.booking.sports.support.BaseIntegrationTest;
import dev.booking.sports.support.IntegrationTest;

@IntegrationTest
class CourtOptimisticLockIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CourtRepository courtRepository;

    @Autowired
    private SportRepository sportRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID ownerId;
    private UUID courtId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into users(id,email,password_hash,full_name,status) values (?,?,?,?,?)",
                ownerId,
                ownerId + "@example.com",
                "unused-test-hash",
                "Optimistic Lock Test",
                "ACTIVE");

        Court court = new Court();
        court.setOwnerId(ownerId);
        court.setSport(sportRepository.findAll().getFirst());
        court.setName("Original court");
        court.setAddress("Test address");
        court.setCity("Test city");
        courtId = courtRepository.saveAndFlush(court).getId();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("delete from courts where id = ?", courtId);
        jdbcTemplate.update("delete from users where id = ?", ownerId);
    }

    @Test
    void concurrentTransactionsUpdatingSameVersion_onlyOneSucceeds() throws Exception {
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

            Court persisted = courtRepository.findById(courtId).orElseThrow();
            assertThat(persisted.getVersion()).isEqualTo(1L);
            assertThat(persisted.getName())
                    .isIn("First update", "Second update");
        } finally {
            beginUpdates.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Long updateInTransaction(
            String name,
            CountDownLatch bothLoaded,
            CountDownLatch beginUpdates) {

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status -> {
            Court court = courtRepository.findById(courtId).orElseThrow();
            assertThat(court.getVersion()).isZero();
            bothLoaded.countDown();
            await(beginUpdates);

            court.setName(name);
            courtRepository.saveAndFlush(court);
            return court.getVersion();
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
