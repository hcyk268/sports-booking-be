package dev.booking.sports.court.domain.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.booking.sports.court.domain.model.Court;

public interface CourtRepository extends JpaRepository<Court, UUID> {
}