package be.transcode.morningdeck.server.core.repository;

import be.transcode.morningdeck.server.core.model.Waitlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface WaitlistRepository extends JpaRepository<Waitlist, UUID> {
    boolean existsByEmail(String email);
}
