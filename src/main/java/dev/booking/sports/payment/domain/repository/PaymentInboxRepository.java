package dev.booking.sports.payment.domain.repository;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import dev.booking.sports.payment.domain.enums.PaymentEnvironment;
import dev.booking.sports.payment.domain.enums.PaymentProvider;
import dev.booking.sports.payment.domain.model.PaymentWebhookInbox;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface PaymentInboxRepository extends JpaRepository<PaymentWebhookInbox, UUID> {

    record Claim(UUID id, UUID token) {}

    @Query(value = """
            SELECT pwi.*
            FROM payment_webhook_inbox pwi
            WHERE (pwi.status = 'PENDING' AND pwi.available_at <= :now)
               OR (pwi.status = 'PROCESSING' AND pwi.locked_until <= :now)
            ORDER BY pwi.available_at, pwi.received_at, pwi.id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<PaymentWebhookInbox> findClaimable(@Param("now") Instant now);

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    default Optional<Claim> claimNext(Instant now, Duration lease) {
        return findClaimable(now).map(inbox -> {
            UUID token = inbox.claim(now, lease);
            saveAndFlush(inbox);
            return new Claim(inbox.getId(), token);
        });
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select inbox from PaymentWebhookInbox inbox where inbox.id = :id")
    Optional<PaymentWebhookInbox> findForUpdate(@Param("id") UUID id);

    Optional<PaymentWebhookInbox> findByProviderAndEnvironmentAndMerchantIdAndDeduplicationKey(
            PaymentProvider provider, PaymentEnvironment environment, String merchantId, String deduplicationKey);
}
