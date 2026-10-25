package dev.booking.sports.booking.domain.model;

import java.util.UUID;

import dev.booking.sports.booking.domain.enums.BookingStatus;
import dev.booking.sports.shared.persistence.CreatableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "booking_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookingStatusHistory extends CreatableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private BookingStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private BookingStatus toStatus;

    @Column(name = "changed_by")
    private UUID changedBy;

    @Column(length = 500)
    private String reason;

    static BookingStatusHistory record(
            Booking booking,
            BookingStatus fromStatus,
            BookingStatus toStatus,
            UUID changedBy,
            String reason) {
        BookingStatusHistory history = new BookingStatusHistory();
        history.booking = booking;
        history.fromStatus = fromStatus;
        history.toStatus = toStatus;
        history.changedBy = changedBy;
        history.reason = reason;
        return history;
    }
}
