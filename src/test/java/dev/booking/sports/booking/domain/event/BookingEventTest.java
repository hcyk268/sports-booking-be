package dev.booking.sports.booking.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class BookingEventTest {

    @Test
    void definesStableOutboxMessageTypes() {
        assertThat(BookingOutboxMessageTypes.BOOKING_AGGREGATE_TYPE)
                .isEqualTo("BOOKING");
        assertThat(BookingOutboxMessageTypes.EVENT_VERSION).isEqualTo(1);
        assertThat(List.of(
                BookingOutboxMessageTypes.BOOKING_HELD,
                BookingOutboxMessageTypes.BOOKING_CONFIRMED,
                BookingOutboxMessageTypes.BOOKING_CANCELLED,
                BookingOutboxMessageTypes.BOOKING_EXPIRED))
                .containsExactly(
                        "booking.held",
                        "booking.confirmed",
                        "booking.cancelled",
                        "booking.expired")
                .doesNotHaveDuplicates();
    }

    @Test
    void heldEventKeepsAnImmutableSlotSnapshot() {
        BookingSlotSnapshot slot = slot();
        List<BookingSlotSnapshot> source = new ArrayList<>(List.of(slot));

        BookingHeldEvent event = new BookingHeldEvent(
                UUID.randomUUID(),
                "BK-20261025-001",
                UUID.randomUUID(),
                new BigDecimal("400000.00"),
                "VND",
                Instant.parse("2026-10-25T02:10:00Z"),
                Instant.parse("2026-10-25T02:00:00Z"),
                source);

        source.clear();

        assertThat(event.slots()).containsExactly(slot);
        assertThatThrownBy(() -> event.slots().add(slot))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void cancelledEventCarriesActorReasonAndOccurrenceTime() {
        UUID actorId = UUID.randomUUID();
        Instant cancelledAt = Instant.parse("2026-10-25T03:00:00Z");

        BookingCancelledEvent event = new BookingCancelledEvent(
                UUID.randomUUID(),
                "BK-20261025-002",
                UUID.randomUUID(),
                new BigDecimal("400000.00"),
                "VND",
                actorId,
                "Customer changed plans",
                cancelledAt,
                List.of(slot()));

        assertThat(event.cancelledBy()).isEqualTo(actorId);
        assertThat(event.cancellationReason())
                .isEqualTo("Customer changed plans");
        assertThat(event.occurredAt()).isEqualTo(cancelledAt);
    }

    @Test
    void rejectsInvalidSlotSnapshot() {
        Instant startAt = Instant.parse("2026-10-25T04:00:00Z");

        assertThatThrownBy(() -> new BookingSlotSnapshot(
                UUID.randomUUID(),
                UUID.randomUUID(),
                startAt,
                startAt,
                new BigDecimal("200000.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("startAt must precede endAt");
    }

    private BookingSlotSnapshot slot() {
        return new BookingSlotSnapshot(
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.parse("2026-10-25T05:00:00Z"),
                Instant.parse("2026-10-25T07:00:00Z"),
                new BigDecimal("400000.00"));
    }
}
