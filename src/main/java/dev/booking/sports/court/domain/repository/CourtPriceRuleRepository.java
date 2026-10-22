package dev.booking.sports.court.domain.repository;

import java.util.List;
import java.time.DayOfWeek;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.booking.sports.court.domain.model.CourtPriceRule;
import org.springframework.stereotype.Repository;

@Repository
public interface CourtPriceRuleRepository
        extends JpaRepository<CourtPriceRule, UUID> {

    List<CourtPriceRule> findByCourt_Id(UUID courtId);

    List<CourtPriceRule> findByCourt_IdInAndDayOfWeek(
            List<UUID> courtIds, DayOfWeek dayOfWeek);
}
