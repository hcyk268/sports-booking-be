package dev.booking.sports.identity.publicapi;

public interface StreamSessionVerifier {
    StreamSession authenticate(String authorizationHeader);
    boolean isActive(StreamSession session);
}
