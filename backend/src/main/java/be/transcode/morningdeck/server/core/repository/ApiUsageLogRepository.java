package be.transcode.morningdeck.server.core.repository;

import be.transcode.morningdeck.server.core.model.ApiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for API usage logs.
 * Query via SQL for now - admin API can be added later.
 */
@Repository
public interface ApiUsageLogRepository extends JpaRepository<ApiUsageLog, UUID> {
}
