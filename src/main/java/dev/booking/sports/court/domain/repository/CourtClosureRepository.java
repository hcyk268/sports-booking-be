package dev.booking.sports.court.domain.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.booking.sports.court.domain.model.CourtClosure;
import org.springframework.stereotype.Repository;

@Repository
public interface CourtClosureRepository
        extends JpaRepository<CourtClosure, UUID> {

    List<CourtClosure>
            findByCourt_IdAndClosureDateOrderByStartTimeAsc(
                    UUID courtId,
                    LocalDate closureDate);

    Optional<CourtClosure> findByIdAndCourt_Id(
            UUID id,
            UUID courtId);

    List<CourtClosure> findByCourt_IdInAndClosureDate(
            List<UUID> courtIds, LocalDate closureDate);
}
