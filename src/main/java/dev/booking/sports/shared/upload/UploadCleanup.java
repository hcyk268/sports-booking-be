package dev.booking.sports.shared.upload;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static dev.booking.sports.shared.upload.UploadAsset.Status.*;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class UploadCleanup {
    private final UploadAssetRepository repository;
    private final ObjectStorage storage;

    @Transactional
    public void clean(UUID id) {
        UploadAsset asset = repository.lockById(id).orElse(null);
        if (asset == null) return;
        boolean expired = List.of(PENDING, READY).contains(asset.getStatus())
                && !asset.getExpiresAt().isAfter(Instant.now());
        if (asset.getStatus() != DELETE_PENDING && !expired) return;
        storage.delete(asset.getStagingKey());
        storage.delete(asset.getStorageKey());
        asset.setStatus(DELETED);
    }
}
