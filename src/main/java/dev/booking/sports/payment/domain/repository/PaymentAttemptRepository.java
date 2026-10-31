package dev.booking.sports.payment.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import dev.booking.sports.payment.domain.enums.PaymentAttemptStatus;
import dev.booking.sports.payment.domain.model.PaymentAttempt;
import dev.booking.sports.payment.domain.enums.PaymentEnvironment;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

@Repository
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {
    List<PaymentAttempt> findByPaymentIdOrderByAttemptNumberDesc(UUID paymentId);

    List<PaymentAttempt> findByPaymentIdInOrderByAttemptNumberDesc(Collection<UUID> paymentIds);

    Optional<PaymentAttempt> findByPaymentIdAndIdempotencyKey(UUID paymentId, String idempotencyKey);

    Optional<PaymentAttempt> findByPaymentIdAndStatusIn(UUID paymentId, Collection<PaymentAttemptStatus> statuses);

    Optional<PaymentAttempt> findFirstByPaymentIdOrderByAttemptNumberDesc(UUID paymentId);

    @Query("select a.paymentId from PaymentAttempt a where a.provider = dev.booking.sports.payment.domain.enums.PaymentProvider.SEPAY and a.environment = :environment and a.merchantId = :merchant and a.invoiceNumber = :invoice")
    Optional<UUID> findPaymentIdByInvoice(@Param("environment") PaymentEnvironment environment,
            @Param("merchant") String merchant, @Param("invoice") String invoice);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PaymentAttempt a where a.provider = dev.booking.sports.payment.domain.enums.PaymentProvider.SEPAY and a.environment = :environment and a.merchantId = :merchant and a.invoiceNumber = :invoice")
    Optional<PaymentAttempt> findInvoiceForUpdate(@Param("environment") PaymentEnvironment environment,
            @Param("merchant") String merchant, @Param("invoice") String invoice);

    boolean existsByProviderAndEnvironmentAndMerchantIdAndProviderOrderIdAndIdNot(
            dev.booking.sports.payment.domain.enums.PaymentProvider provider, PaymentEnvironment environment,
            String merchantId, String orderId, UUID attemptId);
}
