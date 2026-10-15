package dev.booking.sports.court.domain.repository;

import java.util.List;
import java.time.DayOfWeek;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.booking.sports.court.domain.model.CourtOperatingHours;

public interface CourtOperatingHoursRepository
        extends JpaRepository<CourtOperatingHours, UUID> {

    List<CourtOperatingHours> findByCourt_Id(UUID courtId);

    List<CourtOperatingHours> findByCourt_IdInAndDayOfWeek(
            List<UUID> courtIds, DayOfWeek dayOfWeek);
}
