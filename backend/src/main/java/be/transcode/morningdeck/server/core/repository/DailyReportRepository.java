package be.transcode.morningdeck.server.core.repository;

import be.transcode.morningdeck.server.core.model.DailyReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DailyReportRepository extends JpaRepository<DailyReport, UUID> {

    Page<DailyReport> findByDayBriefIdOrderByGeneratedAtDesc(UUID dayBriefId, Pageable pageable);
}
