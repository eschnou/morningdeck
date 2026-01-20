package be.transcode.morningdeck.server.core.repository;

import be.transcode.morningdeck.server.core.model.ReportItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReportItemRepository extends JpaRepository<ReportItem, UUID> {
}
