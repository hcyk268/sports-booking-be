package dev.booking.sports.booking.domain.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import dev.booking.sports.booking.domain.enums.BookingSlotStatus;
import dev.booking.sports.booking.domain.model.BookingSlot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingSlotRepository
        extends JpaRepository<BookingSlot, UUID> {

    @Query("""
            select slot
            from BookingSlot slot
            where slot.courtId = :courtId
              and slot.status in :statuses
              and slot.startAt < :rangeEnd
              and slot.endAt > :rangeStart
            order by slot.startAt
            """)
    List<BookingSlot> findOverlapping(
            @Param("courtId") UUID courtId,
            @Param("rangeStart") Instant rangeStart,
            @Param("rangeEnd") Instant rangeEnd,
            @Param("statuses") Collection<BookingSlotStatus> statuses);
}
