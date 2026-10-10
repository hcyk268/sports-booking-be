/**
 * Outbound notifications.
 */
@org.springframework.modulith.ApplicationModule(
		displayName = "Notification",
		allowedDependencies = { "identity :: events", "shared" })
package dev.booking.sports.notification;
