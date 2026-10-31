package dev.booking.sports.payment.domain.repository;

import java.util.List;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import dev.booking.sports.payment.domain.model.PaymentTransaction;
import dev.booking.sports.payment.domain.enums.PaymentEnvironment;
import dev.booking.sports.payment.domain.enums.PaymentProvider;
import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
    List<PaymentTransaction> findByPaymentIdOrderByReceivedAtDescIdDesc(UUID paymentId);

    List<PaymentTransaction> findByPaymentIdInOrderByReceivedAtDescIdDesc(Collection<UUID> paymentIds);

    Optional<PaymentTransaction> findByProviderAndEnvironmentAndMerchantIdAndProviderTransactionId(
            PaymentProvider provider, PaymentEnvironment environment, String merchantId, String transactionId);
}
