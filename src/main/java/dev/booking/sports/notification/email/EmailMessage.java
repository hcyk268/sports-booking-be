package dev.booking.sports.notification.email;

public record EmailMessage(String to, String toName, String subject, String htmlBody) {
}
