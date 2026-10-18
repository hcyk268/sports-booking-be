package dev.booking.sports.court.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.booking.sports.court.domain.model.Sport;

public interface SportRepository extends JpaRepository<Sport, UUID> {

    Optional<Sport> findByCode(String code);
}