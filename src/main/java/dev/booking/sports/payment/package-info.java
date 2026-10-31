/** Full booking payments, gateway attempts, receipts and manual refunds. */
@org.springframework.modulith.ApplicationModule(
        displayName = "Payment",
        allowedDependencies = { "booking :: api", "shared" })
package dev.booking.sports.payment;
