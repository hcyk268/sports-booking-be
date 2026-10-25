package dev.booking.sports.booking.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import dev.booking.sports.booking.api.dto.request.CreateBookingHoldRequest;
import dev.booking.sports.booking.api.dto.response.BookingResponse;
import dev.booking.sports.booking.api.error.BookingErrorCode;
import dev.booking.sports.booking.config.BookingHoldProperties;
import dev.booking.sports.booking.config.BookingProperties;
import dev.booking.sports.booking.domain.repository.BookingRepository;
import dev.booking.sports.booking.infrastructure.redis.BookingLockManager;
import dev.booking.sports.court.publicapi.CourtFacade;
import dev.booking.sports.court.publicapi.CourtSlotSelection;
import dev.booking.sports.shared.exception.ApiException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingCreateService {

    private final BookingRepository bookingRepository;
    private final CourtFacade courtFacade;
    private final BookingLockManager lockManager;
    private final BookingCreationTransaction creationTransaction;
    private final BookingProperties bookingProperties;
    private final BookingHoldProperties holdProperties;

    public BookingResponse createBooking(UUID customerId, String idempotencyKey, CreateBookingHoldRequest request) {
        List<CourtSlotSelection> selections = normalize(request);
        String requestHash = requestHash(request.courtId(), selections);
        String lockKey = sha256(idempotencyKey);

        try (var ignored = lockManager.acquireIdempotency(customerId, lockKey)) {
            var existing = bookingRepository.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey);
            if (existing.isPresent()) {
                if (!existing.get().hasIdempotencyRequestHash(requestHash)) {
                    throw new ApiException(BookingErrorCode.IDEMPOTENCY_KEY_REUSED);
                }
                return BookingResponse.from(existing.get());
            }

            var quotes = courtFacade.quoteBookableSlots(request.courtId(), selections)
                    .orElseThrow(() -> new ApiException(BookingErrorCode.BOOKING_SLOT_NOT_BOOKABLE));

            List<String> slotKeys = selections.stream()
                    .map(slot -> "booking:slot:" + request.courtId()
                            + ":" + slot.date() + ":" + slot.startTime() + ":" + slot.endTime())
                    .toList();

            try (var slotLocks = lockManager.acquireSlots(slotKeys)) {
                Instant now = Instant.now();

                Instant earliestStart = selections.getFirst().date()
                        .atTime(selections.getFirst().startTime())
                        .atZone(bookingProperties.zoneId()).toInstant();
                if (!earliestStart.isAfter(now)) {
                    throw new ApiException(BookingErrorCode.INVALID_BOOKING_SLOTS);
                }

                Instant holdExpiresAt = now.plus(holdProperties.duration());
                if (holdExpiresAt.isAfter(earliestStart)) {
                    holdExpiresAt = earliestStart;
                }

                try {
                    return creationTransaction.create(
                            customerId,
                            idempotencyKey,
                            requestHash,
                            quotes,
                            bookingProperties.zoneId(),
                            now,
                            holdExpiresAt);
                } catch (DataIntegrityViolationException exception) {
                    if (hasSqlState(exception, "23P01")) {
                        throw new ApiException(BookingErrorCode.BOOKING_SLOT_UNAVAILABLE);
                    }
                    if (hasSqlState(exception, "23505")) {
                        var winner = bookingRepository.findByCustomerIdAndIdempotencyKey(
                                customerId, idempotencyKey);
                        if (winner.isPresent()) {
                            if (!winner.get().hasIdempotencyRequestHash(requestHash)) {
                                throw new ApiException(BookingErrorCode.IDEMPOTENCY_KEY_REUSED);
                            }
                            return BookingResponse.from(winner.get());
                        }
                    }
                    throw exception;
                }
            }
        }
    }

    private List<CourtSlotSelection> normalize(CreateBookingHoldRequest request) {
        if (request == null || request.courtId() == null || request.slots() == null
                || request.slots().isEmpty() || request.slots().size() > 20) {
            throw new ApiException(BookingErrorCode.INVALID_BOOKING_SLOTS);
        }

        List<CourtSlotSelection> selections = new ArrayList<>(request.slots().size());
        for (var slot : request.slots()) {
            if (slot == null || slot.date() == null || slot.startTime() == null
                    || slot.endTime() == null || !slot.startTime().isBefore(slot.endTime())) {
                throw new ApiException(BookingErrorCode.INVALID_BOOKING_SLOTS);
            }
            selections.add(new CourtSlotSelection(slot.date(), slot.startTime(), slot.endTime()));
        }
        selections.sort(Comparator.comparing(CourtSlotSelection::date)
                .thenComparing(CourtSlotSelection::startTime)
                .thenComparing(CourtSlotSelection::endTime));

        for (int index = 1; index < selections.size(); index++) {
            var previous = selections.get(index - 1);
            var current = selections.get(index);
            if (previous.date().equals(current.date())
                    && previous.endTime().isAfter(current.startTime())) {
                throw new ApiException(BookingErrorCode.INVALID_BOOKING_SLOTS);
            }
        }
        return List.copyOf(selections);
    }

    private String requestHash(UUID courtId, List<CourtSlotSelection> slots) {
        StringBuilder canonical = new StringBuilder(courtId.toString());
        slots.forEach(slot -> canonical.append('|')
                .append(slot.date()).append('T')
                .append(slot.startTime()).append('/')
                .append(slot.endTime()));
        return sha256(canonical.toString());
    }

    private String sha256(String input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private boolean hasSqlState(Throwable error, String expected) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && expected.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
