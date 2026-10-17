package dev.booking.sports.court.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import dev.booking.sports.court.domain.enums.CourtStatus;
import dev.booking.sports.court.domain.model.Court;
import jakarta.persistence.criteria.Predicate;

public final class CourtSpecifications {

    private CourtSpecifications() {
    }

    public static Specification<Court> publicCourts(
            String keyword, String city, UUID sportId) {
        return (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            conditions.add(cb.equal(root.get("status"), CourtStatus.ACTIVE));

            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + escapeLike(
                        keyword.trim().toLowerCase(Locale.ROOT)) + "%";
                conditions.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, '\\'),
                        cb.like(cb.lower(root.get("address")), pattern, '\\'),
                        cb.like(cb.lower(root.get("city")), pattern, '\\'),
                        cb.like(cb.lower(root.get("sport").get("name")), pattern, '\\')));
            }
            if (city != null && !city.isBlank()) {
                conditions.add(cb.equal(cb.lower(root.get("city")),
                        city.trim().toLowerCase(Locale.ROOT)));
            }
            if (sportId != null) {
                conditions.add(cb.equal(root.get("sport").get("id"), sportId));
            }
            return cb.and(conditions.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
