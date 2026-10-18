package dev.booking.sports.court.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import dev.booking.sports.court.domain.enums.CourtStatus;
import dev.booking.sports.court.domain.model.Court;

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
}