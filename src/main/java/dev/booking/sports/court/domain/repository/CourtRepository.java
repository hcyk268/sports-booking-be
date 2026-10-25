package dev.booking.sports.court.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dev.booking.sports.court.domain.enums.CourtStatus;
import dev.booking.sports.court.domain.model.Court;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

@Repository
public interface CourtRepository
        extends JpaRepository<Court, UUID>,
                JpaSpecificationExecutor<Court> {

    @Override
    @EntityGraph(attributePaths = "sport")
    Page<Court> findAll(
            Specification<Court> specification,
            Pageable pageable);

    @EntityGraph(attributePaths = "sport")
    Optional<Court> findByIdAndStatus(
            UUID id,
            CourtStatus status);

    @EntityGraph(attributePaths = "sport")
    Page<Court> findByOwnerId(
            UUID ownerId,
            Pageable pageable);

    @EntityGraph(attributePaths = "sport")
    Optional<Court> findByIdAndOwnerId(
            UUID id,
            UUID ownerId);

    @Query("select court.id from Court court where court.ownerId = :ownerId")
    List<UUID> findIdsByOwnerId(@Param("ownerId") UUID ownerId);

    long countByOwnerIdAndIdIn(
            UUID ownerId,
            Collection<UUID> courtIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Court c where c.id = :courtId and c.ownerId = :ownerId")
    Optional<Court> findOwnedCourtForUpdate(
            @Param("courtId") UUID courtId,
            @Param("ownerId") UUID ownerId);
}
