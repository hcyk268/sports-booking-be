package dev.booking.sports.booking.domain.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.booking.sports.booking.domain.enums.BookingStatus;
import dev.booking.sports.booking.domain.model.Booking;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @EntityGraph(attributePaths = "slots")
    Optional<Booking> findByCustomerIdAndIdempotencyKey(
            UUID customerId,
            String idempotencyKey);

    @EntityGraph(attributePaths = "slots")
    Optional<Booking> findByIdAndCustomerId(
            UUID id,
            UUID customerId);

    @EntityGraph(attributePaths = "slots")
    @Query("select booking from Booking booking where booking.id = :bookingId")
    Optional<Booking> findWithSlotsById(
            @Param("bookingId") UUID bookingId);

    @Query(
            value = """
                    select booking
                    from Booking booking
                    where booking.customerId = :customerId
                      and (:status is null or booking.status = :status)
                      and (
                            cast(:rangeStart as Instant) is null
                            or exists (
                                select slot.id
                                from BookingSlot slot
                                where slot.booking = booking
                                  and slot.startAt < :rangeEnd
                                  and slot.endAt > :rangeStart
                            )
                      )
                    order by booking.createdAt desc, booking.id desc
                    """,
            countQuery = """
                    select count(booking)
                    from Booking booking
                    where booking.customerId = :customerId
                      and (:status is null or booking.status = :status)
                      and (
                            cast(:rangeStart as Instant) is null
                            or exists (
                                select slot.id
                                from BookingSlot slot
                                where slot.booking = booking
                                  and slot.startAt < :rangeEnd
                                  and slot.endAt > :rangeStart
                            )
                      )
                    """)
    Page<Booking> findCustomerBookings(
            @Param("customerId") UUID customerId,
            @Param("status") BookingStatus status,
            @Param("rangeStart") Instant rangeStart,
            @Param("rangeEnd") Instant rangeEnd,
            Pageable pageable);

    @Query(
            value = """
                    select booking
                    from Booking booking
                    where (:customerId is null or booking.customerId = :customerId)
                      and (:status is null or booking.status = :status)
                      and (
                            :courtId is null
                            or exists (
                                select courtSlot.id
                                from BookingSlot courtSlot
                                where courtSlot.booking = booking
                                  and courtSlot.courtId = :courtId
                            )
                      )
                      and (
                            cast(:rangeStart as Instant) is null
                            or exists (
                                select dateSlot.id
                                from BookingSlot dateSlot
                                where dateSlot.booking = booking
                                  and dateSlot.startAt < :rangeEnd
                                  and dateSlot.endAt > :rangeStart
                            )
                      )
                    order by booking.createdAt desc, booking.id desc
                    """,
            countQuery = """
                    select count(booking)
                    from Booking booking
                    where (:customerId is null or booking.customerId = :customerId)
                      and (:status is null or booking.status = :status)
                      and (
                            :courtId is null
                            or exists (
                                select courtSlot.id
                                from BookingSlot courtSlot
                                where courtSlot.booking = booking
                                  and courtSlot.courtId = :courtId
                            )
                      )
                      and (
                            cast(:rangeStart as Instant) is null
                            or exists (
                                select dateSlot.id
                                from BookingSlot dateSlot
                                where dateSlot.booking = booking
                                  and dateSlot.startAt < :rangeEnd
                                  and dateSlot.endAt > :rangeStart
                            )
                      )
                    """)
    Page<Booking> findBookings(
            @Param("customerId") UUID customerId,
            @Param("courtId") UUID courtId,
            @Param("status") BookingStatus status,
            @Param("rangeStart") Instant rangeStart,
            @Param("rangeEnd") Instant rangeEnd,
            Pageable pageable);

    @Query(
            value = """
                    select booking
                    from Booking booking
                    where (:customerId is null or booking.customerId = :customerId)
                      and (:status is null or booking.status = :status)
                      and (
                            :courtId is null
                            or exists (
                                select courtSlot.id
                                from BookingSlot courtSlot
                                where courtSlot.booking = booking
                                  and courtSlot.courtId = :courtId
                            )
                      )
                      and (
                            cast(:rangeStart as Instant) is null
                            or exists (
                                select dateSlot.id
                                from BookingSlot dateSlot
                                where dateSlot.booking = booking
                                  and dateSlot.startAt < :rangeEnd
                                  and dateSlot.endAt > :rangeStart
                            )
                      )
                      and not exists (
                            select unmanagedSlot.id
                            from BookingSlot unmanagedSlot
                            where unmanagedSlot.booking = booking
                              and unmanagedSlot.courtId not in :ownedCourtIds
                      )
                    order by booking.createdAt desc, booking.id desc
                    """,
            countQuery = """
                    select count(booking)
                    from Booking booking
                    where (:customerId is null or booking.customerId = :customerId)
                      and (:status is null or booking.status = :status)
                      and (
                            :courtId is null
                            or exists (
                                select courtSlot.id
                                from BookingSlot courtSlot
                                where courtSlot.booking = booking
                                  and courtSlot.courtId = :courtId
                            )
                      )
                      and (
                            cast(:rangeStart as Instant) is null
                            or exists (
                                select dateSlot.id
                                from BookingSlot dateSlot
                                where dateSlot.booking = booking
                                  and dateSlot.startAt < :rangeEnd
                                  and dateSlot.endAt > :rangeStart
                            )
                      )
                      and not exists (
                            select unmanagedSlot.id
                            from BookingSlot unmanagedSlot
                            where unmanagedSlot.booking = booking
                              and unmanagedSlot.courtId not in :ownedCourtIds
                      )
                    """)
    Page<Booking> findProviderBookings(
            @Param("ownedCourtIds") Collection<UUID> ownedCourtIds,
            @Param("customerId") UUID customerId,
            @Param("courtId") UUID courtId,
            @Param("status") BookingStatus status,
            @Param("rangeStart") Instant rangeStart,
            @Param("rangeEnd") Instant rangeEnd,
            Pageable pageable);

    @EntityGraph(attributePaths = "slots")
    @Query("select distinct booking from Booking booking where booking.id in :bookingIds")
    List<Booking> findWithSlotsByIdIn(
            @Param("bookingIds") Collection<UUID> bookingIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b from Booking b
            where b.id = :bookingId
              and b.customerId = :customerId
            """)
    Optional<Booking> findOwnedByIdForUpdate(
            @Param("bookingId") UUID bookingId,
            @Param("customerId") UUID customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :bookingId")
    Optional<Booking> findByIdForUpdate(@Param("bookingId") UUID bookingId);

    @Query(value = """
            select id from bookings
            where (status = 'HELD' and hold_expires_at <= :now)
               or (status = 'PENDING_PAYMENT' and hold_expires_at <= :pendingCutoff)
            order by hold_expires_at, id
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<UUID> lockExpiredHoldIds(@Param("limit") int limit, @Param("now") Instant now,
            @Param("pendingCutoff") Instant pendingCutoff);
}
