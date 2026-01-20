package be.transcode.morningdeck.server.core.repository;

import be.transcode.morningdeck.server.core.model.CreditUsageLog;
import be.transcode.morningdeck.server.core.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CreditUsageLogRepository extends JpaRepository<CreditUsageLog, UUID> {

    List<CreditUsageLog> findByUserOrderByUsedAtDesc(User user);

    Page<CreditUsageLog> findByUser(User user, Pageable pageable);

}
