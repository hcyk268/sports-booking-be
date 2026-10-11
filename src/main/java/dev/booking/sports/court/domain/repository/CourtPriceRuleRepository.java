package dev.booking.sports.court.domain.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.booking.sports.court.domain.model.CourtPriceRule;

public interface CourtPriceRuleRepository
        extends JpaRepository<CourtPriceRule, UUID> {

    List<CourtPriceRule> findByCourt_Id(UUID courtId);
}