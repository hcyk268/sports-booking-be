package dev.booking.sports.review;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;

interface ReviewRepository extends JpaRepository<CourtReview, UUID> {
    Page<CourtReview> findByCourtIdAndHiddenFalseAndDeletedFalse(UUID courtId, Pageable pageable);
    Optional<CourtReview> findByBookingIdAndAuthorIdAndDeletedFalse(UUID bookingId, UUID authorId);
    Page<CourtReview> findByDeletedFalse(Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CourtReview r where r.id = :id and r.deleted = false")
    Optional<CourtReview> lockById(UUID id);
    boolean existsByBookingId(UUID bookingId);
    @Query("select avg(r.stars) from CourtReview r where r.courtId = :courtId and r.hidden = false and r.deleted = false")
    Double averageStars(UUID courtId);
}
