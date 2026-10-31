package dev.booking.sports.payment.domain.model;

import java.util.UUID;
import dev.booking.sports.payment.domain.enums.PaymentStatus;
import dev.booking.sports.shared.persistence.CreatableEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment_status_history")
@Getter
@NoArgsConstructor
public class PaymentStatusHistory extends CreatableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, updatable = false)
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30, updatable = false)
    private PaymentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30, updatable = false)
    private PaymentStatus toStatus;

    @Column(name = "changed_by", updatable = false)
    private UUID changedBy;

    @Column(length = 1000, updatable = false)
    private String reason;

    static PaymentStatusHistory record(Payment payment, PaymentStatus from,
            PaymentStatus to, UUID actorId, String reason) {
        PaymentStatusHistory history = new PaymentStatusHistory();
        history.payment = payment;
        history.fromStatus = from;
        history.toStatus = to;
        history.changedBy = actorId;
        history.reason = PaymentValidation.optionalText(reason, "reason", 1000);
        return history;
    }
}
