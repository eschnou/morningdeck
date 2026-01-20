package be.transcode.morningdeck.server.core.repository;

import be.transcode.morningdeck.server.core.model.RawEmail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RawEmailRepository extends JpaRepository<RawEmail, UUID> {

    boolean existsBySourceIdAndMessageId(UUID sourceId, String messageId);

    Optional<RawEmail> findBySourceIdAndMessageId(UUID sourceId, String messageId);

    Page<RawEmail> findBySourceIdOrderByCreatedAtDesc(UUID sourceId, Pageable pageable);
}
