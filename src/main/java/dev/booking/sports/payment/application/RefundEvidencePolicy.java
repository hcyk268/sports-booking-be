package dev.booking.sports.payment.application;

import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import dev.booking.sports.shared.upload.UploadPolicy;
import dev.booking.sports.shared.exception.ApiException;
import dev.booking.sports.payment.domain.repository.PaymentRefundRepository;
import dev.booking.sports.payment.api.error.PaymentErrorCode;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RefundEvidencePolicy implements UploadPolicy {
    public static final String PURPOSE = "REFUND_EVIDENCE";
    private final PaymentRefundRepository refunds;
    public String purpose() { return PURPOSE; }
    public long maxBytes() { return 5242880; }
    public Set<String> contentTypes() { return Set.of("image/png", "image/jpeg"); }
    public void authorize(UUID actor, UUID id) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN")))
            throw new AccessDeniedException("Admin role is required");
        if (!refunds.existsById(id)) throw new ApiException(PaymentErrorCode.REFUND_NOT_FOUND);
    }
}
