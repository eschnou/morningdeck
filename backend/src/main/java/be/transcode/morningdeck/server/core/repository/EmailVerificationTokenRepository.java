package be.transcode.morningdeck.server.core.repository;

import be.transcode.morningdeck.server.core.model.EmailVerificationToken;
import be.transcode.morningdeck.server.core.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {
    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    void deleteByUser(User user);

    void deleteByExpiresAtBefore(Instant dateTime);

    int countByUserAndCreatedAtAfter(User user, Instant dateTime);
}
