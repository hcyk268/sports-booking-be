package dev.booking.sports.court.application;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import dev.booking.sports.court.api.dto.response.CourtImageResponse;
import dev.booking.sports.court.api.error.CourtErrorCode;
import dev.booking.sports.court.domain.enums.CourtStatus;
import dev.booking.sports.court.domain.model.Court;
import dev.booking.sports.court.domain.model.CourtImage;
import dev.booking.sports.court.domain.repository.CourtRepository;
import dev.booking.sports.court.domain.repository.CourtImageRepository;
import dev.booking.sports.court.infrastructure.storage.LocalCourtImageStorage;
import dev.booking.sports.shared.exception.ApiException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CourtImageService {

    private final CourtRepository courtRepository;
    private final CourtImageRepository imageRepository;
    private final LocalCourtImageStorage storage;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public CourtImageResponse upload(UUID ownerId, UUID courtId, MultipartFile file) {
        Court court = ownedCourt(ownerId, courtId);
        entityManager.lock(court, LockModeType.PESSIMISTIC_WRITE);
        List<CourtImage> gallery = gallery(courtId);
        if (gallery.size() >= 10) {
            throw new ApiException(CourtErrorCode.COURT_IMAGE_LIMIT);
        }

        var stored = storage.save(file);
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            storage.deleteQuietly(stored.storageKey());
                        }
                    }
                });

        CourtImage image = new CourtImage();
        image.setCourt(court);
        image.setStorageKey(stored.storageKey());
        image.setContentType(stored.contentType());
        image.setSizeBytes(stored.sizeBytes());
        image.setDisplayOrder(gallery.stream()
                .mapToInt(CourtImage::getDisplayOrder).max().orElse(-1) + 1);
        image.setCover(gallery.isEmpty());

        return CourtImageResponse.forOwner(imageRepository.saveAndFlush(image));
    }

    @Transactional(readOnly = true)
    public List<CourtImageResponse> listOwned(UUID ownerId, UUID courtId) {
        ownedCourt(ownerId, courtId);
        return gallery(courtId).stream().map(CourtImageResponse::forOwner).toList();
    }

    @Transactional(readOnly = true)
    public List<CourtImageResponse> listPublic(UUID courtId) {
        activeCourt(courtId);
        return gallery(courtId).stream().map(CourtImageResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ImageContent readOwned(UUID ownerId, UUID courtId, UUID imageId) {
        ownedCourt(ownerId, courtId);
        return content(image(courtId, imageId));
    }

    @Transactional(readOnly = true)
    public ImageContent readPublic(UUID courtId, UUID imageId) {
        activeCourt(courtId);
        return content(image(courtId, imageId));
    }

    @Transactional
    public CourtImageResponse setCover(UUID ownerId, UUID courtId, UUID imageId) {
        Court court = ownedCourt(ownerId, courtId);
        entityManager.lock(court, LockModeType.PESSIMISTIC_WRITE);
        CourtImage selected = image(courtId, imageId);
        if (!selected.isCover()) {
            gallery(courtId).forEach(item -> item.setCover(false));
            imageRepository.flush();
            selected.setCover(true);
            imageRepository.flush();
        }
        return CourtImageResponse.forOwner(selected);
    }

    @Transactional
    public void delete(UUID ownerId, UUID courtId, UUID imageId) {
        Court court = ownedCourt(ownerId, courtId);
        entityManager.lock(court, LockModeType.PESSIMISTIC_WRITE);
        CourtImage selected = image(courtId, imageId);
        boolean wasCover = selected.isCover();
        String key = selected.getStorageKey();

        imageRepository.delete(selected);
        imageRepository.flush();
        if (wasCover) {
            List<CourtImage> remaining = gallery(courtId);
            if (!remaining.isEmpty()) {
                remaining.getFirst().setCover(true);
                imageRepository.flush();
            }
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        storage.deleteQuietly(key);
                    }
                });
    }

    private Court ownedCourt(UUID ownerId, UUID courtId) {
        return courtRepository.findByIdAndOwnerId(courtId, ownerId)
                .orElseThrow(() -> new ApiException(CourtErrorCode.COURT_NOT_FOUND));
    }

    private void activeCourt(UUID courtId) {
        courtRepository.findByIdAndStatus(courtId, CourtStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(CourtErrorCode.COURT_NOT_FOUND));
    }

    private CourtImage image(UUID courtId, UUID imageId) {
        return imageRepository.findByIdAndCourt_Id(imageId, courtId)
                .orElseThrow(() -> new ApiException(CourtErrorCode.COURT_IMAGE_NOT_FOUND));
    }

    private List<CourtImage> gallery(UUID courtId) {
        return imageRepository.findByCourt_IdOrderByDisplayOrderAscIdAsc(courtId);
    }

    private ImageContent content(CourtImage image) {
        return new ImageContent(
                storage.read(image.getStorageKey()),
                image.getContentType(),
                image.getStorageKey());
    }

    public record ImageContent(byte[] bytes, String contentType, String fileName) {
    }
}
