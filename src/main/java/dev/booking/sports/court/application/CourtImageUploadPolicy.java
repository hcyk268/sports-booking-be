package dev.booking.sports.court.application;

import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import dev.booking.sports.court.api.error.CourtErrorCode;
import dev.booking.sports.court.domain.repository.CourtImageRepository;
import dev.booking.sports.court.domain.repository.CourtRepository;
import dev.booking.sports.shared.exception.ApiException;
import dev.booking.sports.shared.upload.UploadPolicy;
import lombok.RequiredArgsConstructor;

@Component @RequiredArgsConstructor
public class CourtImageUploadPolicy implements UploadPolicy {
    public static final String PURPOSE = "COURT_IMAGE";
    private final CourtRepository courts;
    private final CourtImageRepository images;
    public String purpose() { return PURPOSE; }
    public long maxBytes() { return 5242880; }
    public Set<String> contentTypes() { return Set.of("image/png", "image/jpeg"); }
    public void authorize(UUID actor, UUID courtId) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(authority -> authority.getAuthority().equals("ROLE_PROVIDER")))
            throw new AccessDeniedException("Provider role is required");
        courts.findByIdAndOwnerId(courtId, actor)
                .orElseThrow(() -> new ApiException(CourtErrorCode.COURT_NOT_FOUND));
    }
    public void checkCapacity(UUID courtId) {
        if (images.countByCourt_Id(courtId) >= 10) throw new ApiException(CourtErrorCode.COURT_IMAGE_LIMIT);
    }
}
