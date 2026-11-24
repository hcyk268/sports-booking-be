package dev.booking.sports.payment.domain.repository;

import java.util.List;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import dev.booking.sports.payment.domain.model.PaymentRefund;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<PaymentRefund> {
    List<PaymentRefund> findByPaymentIdOrderByCreatedAtDescIdDesc(UUID paymentId);

    List<PaymentRefund> findByPaymentIdInOrderByCreatedAtDescIdDesc(Collection<UUID> paymentIds);
    java.util.Optional<PaymentRefund> findByPaymentIdAndIdempotencyKey(UUID paymentId, String key);

    java.util.Optional<PaymentRefund> findByPaymentIdAndCompletionKey(UUID paymentId, String key);

    @org.springframework.data.jpa.repository.Query("select coalesce(sum(r.amount), 0) from PaymentRefund r where r.paymentTransactionId = :id and r.status in :statuses")
    java.math.BigDecimal reserved(UUID id, Collection<dev.booking.sports.payment.domain.enums.PaymentRefundStatus> statuses);

    @org.springframework.data.jpa.repository.Query("select r.paymentId from PaymentRefund r where r.id = :id")
    java.util.Optional<UUID> paymentId(UUID id);
}
