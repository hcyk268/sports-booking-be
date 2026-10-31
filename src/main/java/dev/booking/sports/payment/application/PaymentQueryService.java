package dev.booking.sports.payment.application;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.booking.sports.booking.publicapi.BookingPaymentFacade;
import dev.booking.sports.booking.publicapi.BookingPaymentSnapshot;
import dev.booking.sports.payment.domain.model.*;
import dev.booking.sports.payment.api.error.PaymentErrorCode;
import dev.booking.sports.shared.api.PageResponse;
import dev.booking.sports.shared.exception.ApiException;
import dev.booking.sports.payment.api.dto.response.*;
import dev.booking.sports.payment.domain.enums.PaymentStatus;
import dev.booking.sports.payment.domain.repository.*;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentQueryService {
    private final BookingPaymentFacade bookingFacade;
    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final PaymentRefundRepository refundRepository;

    @Transactional(readOnly = true)
    public List<PaymentResponse> getBookingPayments(UUID actorId, Set<String> authorities, UUID bookingId) {
        var booking = bookingFacade.getAccessibleBooking(actorId, authorities, bookingId);
        var payment = paymentRepository.findByBookingId(bookingId).orElse(null);
        if (payment == null) {
            return List.of();
        }
        return List.of(toResponse(payment, booking));
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID actorId, Set<String> authorities, UUID paymentId) {
        var payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        try {
            return toResponse(payment, bookingFacade.getAccessibleBooking(actorId, authorities, payment.getBookingId()));
        } catch (ApiException exception) {
            if (exception.getErrorCode().status() == HttpStatus.NOT_FOUND) {
                throw new ApiException(PaymentErrorCode.PAYMENT_NOT_FOUND);
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> listPayments(UUID actorId, Set<String> authorities,
            UUID bookingId, UUID customerId, PaymentStatus status, Instant from, Instant to, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(PaymentErrorCode.INVALID_PAYMENT_PAGINATION);
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new ApiException(PaymentErrorCode.INVALID_PAYMENT_DATE_RANGE);
        }
        boolean admin = authorities.contains("ROLE_ADMIN");
        boolean customer = authorities.contains("ROLE_CUSTOMER");
        boolean provider = authorities.contains("ROLE_PROVIDER");
        if (!admin && !customer && !provider) {
            throw new ApiException(PaymentErrorCode.PAYMENT_ACCESS_DENIED);
        }
        var result = paymentRepository.findAccessiblePayments(actorId, admin, customer, provider,
                bookingId, customerId, status == null ? null : status.name(), from, to, PageRequest.of(page, size));
        if (result.isEmpty()) {
            return PageResponse.of(List.of(), result.getNumber(), result.getSize(), result.getTotalElements());
        }
        var paymentIds = result.getContent().stream().map(Payment::getId).toList();
        var bookingIds = result.getContent().stream().map(Payment::getBookingId).toList();
        var bookings = bookingFacade.getAccessibleBookings(actorId, authorities, bookingIds);
        var attempts = attemptRepository.findByPaymentIdInOrderByAttemptNumberDesc(paymentIds).stream()
                .collect(Collectors.groupingBy(PaymentAttempt::getPaymentId));
        var transactions = transactionRepository.findByPaymentIdInOrderByReceivedAtDescIdDesc(paymentIds).stream()
                .collect(Collectors.groupingBy(PaymentTransaction::getPaymentId));
        var refunds = refundRepository.findByPaymentIdInOrderByCreatedAtDescIdDesc(paymentIds).stream()
                .collect(Collectors.groupingBy(PaymentRefund::getPaymentId));
        Instant now = Instant.now();
        var items = result.getContent().stream()
                .map(payment -> toResponse(payment, bookings.get(payment.getBookingId()),
                        attempts.getOrDefault(payment.getId(), List.of()),
                        transactions.getOrDefault(payment.getId(), List.of()),
                        refunds.getOrDefault(payment.getId(), List.of()), now))
                .toList();
        return PageResponse.of(items, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    private PaymentResponse toResponse(Payment payment, BookingPaymentSnapshot booking) {
        var attempts = attemptRepository.findByPaymentIdOrderByAttemptNumberDesc(payment.getId());
        var transactions = transactionRepository.findByPaymentIdOrderByReceivedAtDescIdDesc(payment.getId());
        var refunds = refundRepository.findByPaymentIdOrderByCreatedAtDescIdDesc(payment.getId());
        return toResponse(payment, booking, attempts, transactions, refunds, Instant.now());
    }

    private PaymentResponse toResponse(Payment payment, BookingPaymentSnapshot booking,
            List<PaymentAttempt> attempts, List<PaymentTransaction> transactions, List<PaymentRefund> refunds,
            Instant now) {
        boolean canResume = booking.awaitingPayment() && booking.holdExpiresAt() != null
                && now.isBefore(booking.holdExpiresAt()) && payment.getStatus() == PaymentStatus.PENDING
                && now.isBefore(payment.getExpiresAt())
                && attempts.stream().anyMatch(attempt -> attempt.canResumeCheckout(now));

        return new PaymentResponse(payment.getId(), payment.getBookingId(), payment.getAmount(),
                payment.getCurrency(), payment.getStatus(), payment.getExpiresAt(), payment.getSettledTransactionId(),
                payment.getSettledAt(), canResume, payment.getVersion(), payment.getCreatedAt(), payment.getUpdatedAt(),
                attempts.stream().map(PaymentAttemptResponse::from).toList(),
                transactions.stream().map(PaymentTransactionResponse::from).toList(),
                refunds.stream().map(PaymentRefundSummaryResponse::from).toList());
    }
}
