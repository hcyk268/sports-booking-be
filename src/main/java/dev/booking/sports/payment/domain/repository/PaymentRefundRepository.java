package dev.booking.sports.payment.domain.repository;

import java.util.List;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import dev.booking.sports.payment.domain.model.PaymentRefund;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, UUID> {
    List<PaymentRefund> findByPaymentIdOrderByCreatedAtDescIdDesc(UUID paymentId);

    List<PaymentRefund> findByPaymentIdInOrderByCreatedAtDescIdDesc(Collection<UUID> paymentIds);
}
