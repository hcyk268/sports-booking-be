package dev.booking.sports.identity.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.booking.sports.identity.domain.repository.UserRepository;
import dev.booking.sports.identity.publicapi.EmailRecipient;
import dev.booking.sports.identity.publicapi.EmailRecipientDirectory;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailRecipientDirectoryAdapter implements EmailRecipientDirectory {
    private final UserRepository users;

    @Override
    @Transactional(readOnly = true)
    public Optional<EmailRecipient> findByUserId(UUID userId) {
        return users.findById(userId).map(user -> new EmailRecipient(user.getEmail(), user.getFullName()));
    }
}
