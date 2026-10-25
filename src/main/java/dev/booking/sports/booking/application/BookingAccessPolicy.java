package dev.booking.sports.booking.application;

import java.util.List;
import java.util.UUID;

import dev.booking.sports.booking.domain.model.Booking;
import dev.booking.sports.court.publicapi.CourtFacade;

final class BookingAccessPolicy {

    private BookingAccessPolicy() {
    }

    static boolean canManage(
            BookingViewer viewer,
            Booking booking,
            CourtFacade courtFacade) {
        if (viewer.hasRole(BookingActorRole.ADMIN)) {
            return true;
        }
        if (viewer.hasRole(BookingActorRole.CUSTOMER)
                && viewer.userId().equals(booking.getCustomerId())) {
            return true;
        }
        if (!viewer.hasRole(BookingActorRole.PROVIDER)) {
            return false;
        }

        List<UUID> courtIds = booking.getSlots().stream()
                .map(slot -> slot.getCourtId())
                .distinct()
                .toList();
        return courtFacade.ownsAllCourts(viewer.userId(), courtIds);
    }
}
