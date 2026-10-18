package dev.booking.sports.shared.upload;

import java.time.Instant;
import java.util.UUID;
import dev.booking.sports.shared.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "shared_upload_assets")
@Getter @Setter @NoArgsConstructor
public class UploadAsset extends BaseEntity {
    public enum Status { PENDING, READY, ATTACHED, DELETE_PENDING, DELETED }
    @Column(nullable = false) private UUID ownerId;
    @Column(nullable = false, length = 50) private String purpose;
    @Column(nullable = false) private UUID resourceId;
    @Column(nullable = false, length = 120) private String fileName;
    @Column(nullable = false, length = 40) private String contentType;
    @Column(nullable = false) private long sizeBytes;
    @Column(nullable = false, unique = true, length = 255) private String stagingKey;
    @Column(nullable = false, unique = true, length = 255) private String storageKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(nullable = false) private Instant expiresAt;
    @Column(length = 100) private String etag;
}
