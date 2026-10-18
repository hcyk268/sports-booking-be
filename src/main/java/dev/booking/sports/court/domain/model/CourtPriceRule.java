package dev.booking.sports.court.domain.model;

import java.math.BigDecimal;
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
        name = "court_price_rules",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_court_price_rules_start",
                columnNames = {
                        "court_id",
                        "day_of_week",
                        "start_time"
                }))
@Getter
@Setter
@NoArgsConstructor
public class CourtPriceRule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "court_id", nullable = false)
    private Court court;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 9)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(
            name = "price_per_hour",
            nullable = false,
            precision = 12,
            scale = 2)
    private BigDecimal pricePerHour;
}