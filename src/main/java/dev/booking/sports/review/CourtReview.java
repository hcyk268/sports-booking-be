package dev.booking.sports.review;

import java.time.Instant;
import java.util.UUID;
import dev.booking.sports.shared.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "court_reviews")
@Getter
class CourtReview extends BaseEntity {
    @Column(nullable = false, updatable = false) UUID bookingId;
    @Column(nullable = false, updatable = false) UUID courtId;
    @Column(nullable = false, updatable = false) UUID authorId;
    protected CourtReview() {}

    int stars;
    String comment;
    String reply;
    boolean hidden;
    boolean deleted;
    String moderationReason;
    UUID moderatedBy;
    Instant moderatedAt;
    @Version Long version;
}
