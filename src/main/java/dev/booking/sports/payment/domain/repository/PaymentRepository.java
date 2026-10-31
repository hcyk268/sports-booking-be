package dev.booking.sports.payment.domain.repository;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import dev.booking.sports.payment.domain.model.Payment;
import jakarta.persistence.LockModeType;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    @Query(
            value = """
                    select p.*
                    from payments p join bookings b on b.id = p.booking_id
                    where (cast(:bookingId as uuid) is null or p.booking_id = :bookingId)
                      and (cast(:customerId as uuid) is null or b.customer_id = :customerId)
                      and (cast(:status as varchar) is null or p.status = :status)
                      and (cast(:from as timestamptz) is null or p.created_at >= :from)
                      and (cast(:to as timestamptz) is null or p.created_at < :to)
                      and (
                          :admin = true
                          or (:customer = true and b.customer_id = :actorId)
                          or (:provider = true
                              and exists (select 1 from booking_slots s where s.booking_id = b.id)
                              and not exists (
                                  select 1 from booking_slots s join courts c on c.id = s.court_id
                                  where s.booking_id = b.id and c.owner_id <> :actorId
                              ))
                      )
                    order by p.created_at desc, p.id desc
                    """,
            countQuery = """
                    select count(*)
                    from payments p join bookings b on b.id = p.booking_id
                    where (cast(:bookingId as uuid) is null or p.booking_id = :bookingId)
                      and (cast(:customerId as uuid) is null or b.customer_id = :customerId)
                      and (cast(:status as varchar) is null or p.status = :status)
                      and (cast(:from as timestamptz) is null or p.created_at >= :from)
                      and (cast(:to as timestamptz) is null or p.created_at < :to)
                      and (
                          :admin = true
                          or (:customer = true and b.customer_id = :actorId)
                          or (:provider = true
                              and exists (select 1 from booking_slots s where s.booking_id = b.id)
                              and not exists (
                                  select 1 from booking_slots s join courts c on c.id = s.court_id
                                  where s.booking_id = b.id and c.owner_id <> :actorId
                              ))
                      )
                    """,
            nativeQuery = true)
    Page<Payment> findAccessiblePayments(@Param("actorId") UUID actorId,
            @Param("admin") boolean admin, @Param("customer") boolean customer, @Param("provider") boolean provider,
            @Param("bookingId") UUID bookingId, @Param("customerId") UUID customerId,
            @Param("status") String status, @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    Optional<Payment> findByBookingId(UUID bookingId);

    @Query("select p.bookingId from Payment p where p.id = :id")
    Optional<UUID> findBookingIdById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.bookingId = :bookingId")
    Optional<Payment> findByBookingIdForUpdate(@Param("bookingId") UUID bookingId);
}
