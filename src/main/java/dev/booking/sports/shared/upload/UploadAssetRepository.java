package dev.booking.sports.shared.upload;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface UploadAssetRepository extends JpaRepository<UploadAsset, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from UploadAsset a where a.id = :id")
    Optional<UploadAsset> lockById(@Param("id") UUID id);

    long countByOwnerIdAndStatusInAndExpiresAtAfter(UUID ownerId, List<UploadAsset.Status> states, Instant now);

    @Query("select a.id from UploadAsset a where a.status = :deleteStatus "
         + "or (a.status in :states and a.expiresAt < :now) order by a.expiresAt")
    List<UUID> cleanupCandidates(@Param("deleteStatus") UploadAsset.Status deleteStatus,
                                @Param("states") List<UploadAsset.Status> states,
                                @Param("now") Instant now, Pageable pageable);
}
