/**
 * Outbound notifications.
 */
@org.springframework.modulith.ApplicationModule(
		displayName = "Notification",
		allowedDependencies = { "identity :: events", "identity :: api", "booking :: events", "shared" })
package dev.booking.sports.notification;
