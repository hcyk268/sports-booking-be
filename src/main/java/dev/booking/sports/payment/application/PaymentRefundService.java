package dev.booking.sports.payment.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import dev.booking.sports.payment.api.dto.request.*;
import dev.booking.sports.payment.api.dto.response.PaymentRefundResponse;
import dev.booking.sports.payment.domain.enums.PaymentRefundStatus;
import dev.booking.sports.payment.domain.model.PaymentRefund;
import dev.booking.sports.payment.domain.repository.*;
import dev.booking.sports.shared.api.PageResponse;
import dev.booking.sports.shared.exception.ApiException;
import dev.booking.sports.shared.exception.ConcurrencyErrorCode;
import dev.booking.sports.shared.upload.UploadService;
import lombok.RequiredArgsConstructor;
import static dev.booking.sports.payment.api.error.PaymentErrorCode.*;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentRefundService {
    private final PaymentRepository payments;
    private final PaymentRefundRepository refunds;
    private final PaymentTransactionRepository receipts;
    private final UploadService uploads;

    public PaymentRefundResponse create(UUID admin, String key, CreateRefundRequest request) {
        payments.findByIdForUpdate(request.paymentId()).orElseThrow(() -> new ApiException(PAYMENT_NOT_FOUND));
        String hash = PaymentRequestHash.of(request.paymentTransactionId(), request.amount().stripTrailingZeros().toPlainString(), request.reason());
        var existing = refunds.findByPaymentIdAndIdempotencyKey(request.paymentId(), key);
        if (existing.isPresent()) {
            if (!existing.get().hasIdempotencyRequestHash(hash)) throw new ApiException(PAYMENT_IDEMPOTENCY_KEY_REUSED);
            return PaymentRefundResponse.from(existing.get());
        }
        var receipt = receipts.lockById(request.paymentTransactionId())
                .filter(value -> request.paymentId().equals(value.getPaymentId()))
                .orElseThrow(() -> new ApiException(PAYMENT_TRANSACTION_NOT_FOUND));
        if (!receipt.isApprovedPayment() || !"VND".equals(receipt.getCurrency())) throw new ApiException(REFUND_TRANSACTION_NOT_ELIGIBLE);
        var reserved = refunds.reserved(receipt.getId(), List.of(PaymentRefundStatus.REQUESTED,
                PaymentRefundStatus.PROCESSING, PaymentRefundStatus.COMPLETED));
        if (reserved.add(request.amount()).compareTo(receipt.getAmount()) > 0) throw new ApiException(REFUND_AMOUNT_EXCEEDED);
        return save(PaymentRefund.request(receipt, request.amount(), request.reason(), key, hash, admin));
    }

    @Transactional(readOnly = true)
    public PaymentRefundResponse get(UUID id) {
        return PaymentRefundResponse.from(refunds.findById(id).orElseThrow(() -> new ApiException(REFUND_NOT_FOUND)));
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentRefundResponse> list(UUID payment, UUID receipt, PaymentRefundStatus status,
            Instant from, Instant to, int page, int size) {
        if (from != null && to != null && from.isAfter(to)) throw new ApiException(INVALID_PAYMENT_DATE_RANGE);
        Specification<PaymentRefund> filter = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (payment != null) predicates.add(cb.equal(root.get("paymentId"), payment));
            if (receipt != null) predicates.add(cb.equal(root.get("paymentTransactionId"), receipt));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) predicates.add(cb.lessThan(root.get("createdAt"), to));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var rows = refunds.findAll(filter, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return PageResponse.of(rows.map(PaymentRefundResponse::from).getContent(), page, size, rows.getTotalElements());
    }

    private PaymentRefund locked(UUID id) {
        UUID paymentId = refunds.paymentId(id).orElseThrow(() -> new ApiException(REFUND_NOT_FOUND));
        payments.findByIdForUpdate(paymentId).orElseThrow(() -> new ApiException(PAYMENT_NOT_FOUND));
        return refunds.findById(id).orElseThrow(() -> new ApiException(REFUND_NOT_FOUND));
    }

    private void version(PaymentRefund refund, long expected) {
        if (refund.getVersion() != expected) throw new ApiException(ConcurrencyErrorCode.OPTIMISTIC_LOCK_CONFLICT);
    }

    private PaymentRefundResponse save(PaymentRefund refund) {
        if (refund.getId() == null) refund = refunds.save(refund);
        // Existing aggregates are already managed; merging would copy new history without MERGE cascade.
        refunds.flush();
        return PaymentRefundResponse.from(refund);
    }

    public PaymentRefundResponse start(UUID id, UUID admin, long expected) {
        var refund = locked(id);
        version(refund, expected);
        if (refund.getStatus() != PaymentRefundStatus.REQUESTED) throw new ApiException(REFUND_PROCESSING_NOT_ALLOWED);
        refund.startProcessing(admin, Instant.now());
        return save(refund);
    }

    public PaymentRefundResponse complete(UUID id, UUID admin, String key, CompleteRefundRequest request) {
        var refund = locked(id);
        String hash = PaymentRequestHash.of(id, request.version(), request.refundReference(), request.evidenceAssetId(), request.adminNote());
        var existing = refunds.findByPaymentIdAndCompletionKey(refund.getPaymentId(), key);
        if (existing.isPresent()) {
            if (!id.equals(existing.get().getId()) || !hash.equals(existing.get().getCompletionHash()))
                throw new ApiException(PAYMENT_IDEMPOTENCY_KEY_REUSED);
            return PaymentRefundResponse.from(existing.get());
        }
        version(refund, request.version());
        if (refund.getStatus() != PaymentRefundStatus.PROCESSING) throw new ApiException(REFUND_COMPLETION_NOT_ALLOWED);
        if (request.evidenceAssetId() != null) {
            try {
                uploads.claim(admin, request.evidenceAssetId(), RefundEvidencePolicy.PURPOSE, id);
            } catch (ApiException exception) {
                throw new ApiException(REFUND_EVIDENCE_INVALID);
            }
        }
        String note = request.adminNote() == null || request.adminNote().isBlank() ? null : request.adminNote();
        refund.complete(admin, request.refundReference(), request.evidenceAssetId(), note, Instant.now());
        refund.recordCompletionRequest(key, hash);
        return save(refund);
    }

    public PaymentRefundResponse decide(UUID id, UUID admin, RefundDecisionRequest request, boolean reject) {
        var refund = locked(id);
        version(refund, request.version());
        if (refund.getStatus() != PaymentRefundStatus.REQUESTED)
            throw new ApiException(reject ? REFUND_REJECTION_NOT_ALLOWED : REFUND_CANCELLATION_NOT_ALLOWED);
        if (reject) refund.reject(admin, request.reason());
        else refund.cancel(admin, request.reason());
        return save(refund);
    }
}
