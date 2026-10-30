package dev.booking.sports.payment.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import dev.booking.sports.payment.domain.enums.PaymentAttemptStatus;
import dev.booking.sports.payment.domain.model.PaymentAttempt;

@Repository
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {
    List<PaymentAttempt> findByPaymentIdOrderByAttemptNumberDesc(UUID paymentId);

    Optional<PaymentAttempt> findByPaymentIdAndIdempotencyKey(UUID paymentId, String idempotencyKey);

    Optional<PaymentAttempt> findByPaymentIdAndStatusIn(UUID paymentId, Collection<PaymentAttemptStatus> statuses);

    Optional<PaymentAttempt> findFirstByPaymentIdOrderByAttemptNumberDesc(UUID paymentId);
}
