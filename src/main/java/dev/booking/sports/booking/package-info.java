/**
 * Booking holds, reservations, and availability occupancy.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Booking",
        allowedDependencies = { "court :: api", "shared" })
package dev.booking.sports.booking;
