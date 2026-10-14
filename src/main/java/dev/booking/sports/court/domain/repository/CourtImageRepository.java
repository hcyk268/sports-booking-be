package dev.booking.sports.court.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import dev.booking.sports.court.domain.model.CourtImage;

public interface CourtImageRepository extends JpaRepository<CourtImage, UUID> {
    List<CourtImage> findByCourt_IdOrderByDisplayOrderAscIdAsc(UUID courtId);
    Optional<CourtImage> findByIdAndCourt_Id(UUID id, UUID courtId);
}
