package dev.booking.sports.court.domain.model;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;

import dev.booking.sports.court.domain.enums.CourtStatus;
import dev.booking.sports.shared.persistence.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "courts")
@Getter
@Setter
@NoArgsConstructor
public class Court extends BaseEntity {

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sport_id", nullable = false)
    private Sport sport;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(length = 100)
    private String ward;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "court_amenities",
            joinColumns = @JoinColumn(name = "court_id"))
    @Column(name = "amenity", nullable = false, length = 100)
    @BatchSize(size = 50)
    private Set<String> amenities = new LinkedHashSet<>();

    @Column(columnDefinition = "TEXT")
    private String rules;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CourtStatus status = CourtStatus.DRAFT;
}
