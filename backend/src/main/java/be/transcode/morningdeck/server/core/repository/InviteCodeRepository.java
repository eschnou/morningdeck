package be.transcode.morningdeck.server.core.repository;

import be.transcode.morningdeck.server.core.model.InviteCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface InviteCodeRepository extends JpaRepository<InviteCode, UUID> {
    Optional<InviteCode> findByCodeIgnoreCase(String code);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE InviteCode i SET i.useCount = i.useCount + 1 WHERE i.id = :id")
    int incrementUseCount(@Param("id") UUID id);
}
