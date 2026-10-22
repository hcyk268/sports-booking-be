package dev.booking.sports.court.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;

import dev.booking.sports.court.domain.enums.CourtStatus;
import dev.booking.sports.shared.persistence.BaseEntity;
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

    @OneToMany(mappedBy = "court", fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC, id ASC")
    @BatchSize(size = 50)
    private List<CourtImage> images = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CourtStatus status = CourtStatus.DRAFT;

    @Version
    @Column(nullable = false)
    private Long version;
}
