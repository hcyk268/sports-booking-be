package dev.booking.sports.payment.domain.model;

import java.util.UUID;
import dev.booking.sports.payment.domain.enums.PaymentRefundStatus;
import dev.booking.sports.shared.persistence.CreatableEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment_refund_status_history")
@Getter
@NoArgsConstructor
public class PaymentRefundStatusHistory extends CreatableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "refund_id", nullable = false, updatable = false)
    private PaymentRefund refund;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30, updatable = false)
    private PaymentRefundStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30, updatable = false)
    private PaymentRefundStatus toStatus;

    @Column(name = "changed_by", updatable = false)
    private UUID changedBy;

    @Column(length = 1000, updatable = false)
    private String reason;

    static PaymentRefundStatusHistory record(PaymentRefund refund, PaymentRefundStatus from,
            PaymentRefundStatus to, UUID actorId, String reason) {
        PaymentRefundStatusHistory history = new PaymentRefundStatusHistory();
        history.refund = refund;
        history.fromStatus = from;
        history.toStatus = to;
        history.changedBy = actorId;
        history.reason = PaymentValidation.optionalText(reason, "reason", 1000);
        return history;
    }
}
