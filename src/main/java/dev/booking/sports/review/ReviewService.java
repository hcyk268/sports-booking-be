package dev.booking.sports.review;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import dev.booking.sports.booking.publicapi.ReviewEligibility;
import dev.booking.sports.court.publicapi.CourtFacade;
import dev.booking.sports.shared.api.PageResponse;
import dev.booking.sports.shared.exception.ApiException;
import lombok.RequiredArgsConstructor;
import static dev.booking.sports.review.ReviewErrors.*;

@Service
@Transactional
@RequiredArgsConstructor
class ReviewService {
    private final ReviewRepository reviews;
    private final ReviewEligibility eligibility;
    private final CourtFacade courts;

    record Entry(UUID id, UUID courtId, int stars, String comment, String reply, Instant createdAt, long version) {
        static Entry from(CourtReview r) {
            return new Entry(r.getId(), r.courtId, r.stars, r.comment, r.reply, r.getCreatedAt(), r.version);
        }
    }
    record Managed(Entry review, boolean hidden, String reason) {
        static Managed from(CourtReview r) {
            return new Managed(Entry.from(r), r.hidden, r.moderationReason);
        }
    }
    record Feed(Double averageStars, PageResponse<Entry> reviews) {}

    private PageRequest page(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }
    @Transactional(readOnly = true)
    public Feed list(UUID court, int page, int size) {
        if (!courts.isPublished(court)) throw new ApiException(REVIEW_NOT_FOUND);
        Page<Entry> rows = reviews.findByCourtIdAndHiddenFalseAndDeletedFalse(court, page(page, size)).map(Entry::from);
        return new Feed(reviews.averageStars(court), PageResponse.of(rows.getContent(), page, size, rows.getTotalElements()));
    }
    @Transactional(readOnly = true)
    public PageResponse<Managed> moderationQueue(int page, int size) {
        var rows = reviews.findByDeletedFalse(page(page, size)).map(Managed::from);
        return PageResponse.of(rows.getContent(), page, size, rows.getTotalElements());
    }
    @Transactional(readOnly = true)
    public Managed own(UUID booking, UUID actor) {
        return Managed.from(reviews.findByBookingIdAndAuthorIdAndDeletedFalse(booking, actor)
                .orElseThrow(() -> new ApiException(REVIEW_NOT_FOUND)));
    }
    public Managed create(UUID booking, UUID court, UUID actor, int stars, String comment) {
        if (!eligibility.completedByCustomer(booking, actor, court)) throw new ApiException(BOOKING_NOT_REVIEWABLE);
        if (reviews.existsByBookingId(booking)) throw new ApiException(REVIEW_ALREADY_EXISTS);
        var review = new CourtReview();
        review.bookingId = booking;
        review.courtId = court;
        review.authorId = actor;
        review.stars = stars;
        review.comment = comment.strip();
        return Managed.from(reviews.saveAndFlush(review));
    }
    private CourtReview locked(UUID id) {
        return reviews.lockById(id).orElseThrow(() -> new ApiException(REVIEW_NOT_FOUND));
    }
    private void checkVersion(CourtReview r, long expected) {
        if (r.version != expected) throw new ApiException(REVIEW_CONFLICT);
    }
    private CourtReview owned(UUID id, UUID actor, long version) {
        var r = locked(id);
        if (!r.authorId.equals(actor)) throw new ApiException(REVIEW_NOT_FOUND);
        checkVersion(r, version);
        return r;
    }
    public Managed edit(UUID id, UUID actor, long version, int stars, String comment) {
        var r = owned(id, actor, version);
        r.stars = stars;
        r.comment = comment.strip();
        return Managed.from(reviews.saveAndFlush(r));
    }
    public void delete(UUID id, UUID actor, long version) {
        var review = owned(id, actor, version);
        review.deleted = true;
        review.comment = "";
        review.reply = null;
    }
    public Entry reply(UUID id, UUID actor, long version, String text) {
        var r = locked(id);
        if (r.hidden || !courts.ownsAllCourts(actor, Set.of(r.courtId))) throw new ApiException(REVIEW_NOT_FOUND);
        checkVersion(r, version);
        r.reply = text.strip();
        return Entry.from(reviews.saveAndFlush(r));
    }
    public Managed moderate(UUID id, UUID actor, long version, boolean hidden, String reason) {
        var r = locked(id);
        checkVersion(r, version);
        r.hidden = hidden;
        r.moderationReason = reason.strip();
        r.moderatedBy = actor;
        r.moderatedAt = Instant.now();
        return Managed.from(reviews.saveAndFlush(r));
    }
}
