package dev.booking.sports.payment.domain.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import dev.booking.sports.payment.domain.model.PaymentRefund;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, UUID> {
    List<PaymentRefund> findByPaymentIdOrderByCreatedAtDescIdDesc(UUID paymentId);
}
