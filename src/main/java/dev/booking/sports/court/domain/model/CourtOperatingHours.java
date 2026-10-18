package dev.booking.sports.court.domain.model;

import java.time.DayOfWeek;
import java.time.LocalTime;

import dev.booking.sports.shared.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "court_operating_hours",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_court_operating_hours_day",
                columnNames = {"court_id", "day_of_week"}))
@Getter
@Setter
@NoArgsConstructor
public class CourtOperatingHours extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "court_id", nullable = false)
    private Court court;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 9)
    private DayOfWeek dayOfWeek;

    @Column(name = "opening_time", nullable = false)
    private LocalTime openingTime;

    @Column(name = "closing_time", nullable = false)
    private LocalTime closingTime;

    @Column(name = "slot_duration_minutes", nullable = false)
    private int slotDurationMinutes;
}