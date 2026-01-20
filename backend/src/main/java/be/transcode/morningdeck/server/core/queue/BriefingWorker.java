package be.transcode.morningdeck.server.core.queue;

import be.transcode.morningdeck.server.core.dto.DailyReportDTO;
import be.transcode.morningdeck.server.core.dto.ReportItemDTO;
import be.transcode.morningdeck.server.core.model.*;
import be.transcode.morningdeck.server.core.repository.DailyReportRepository;
import be.transcode.morningdeck.server.core.repository.DayBriefRepository;
import be.transcode.morningdeck.server.core.repository.NewsItemRepository;
import be.transcode.morningdeck.server.core.service.ReportEmailDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Worker component that processes briefing execution jobs.
 * Handles the full pipeline: QUEUED -> PROCESSING -> ACTIVE/ERROR
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BriefingWorker {

    private final DayBriefRepository dayBriefRepository;
    private final NewsItemRepository newsItemRepository;
    private final DailyReportRepository dailyReportRepository;
    private final ReportEmailDeliveryService reportEmailDeliveryService;

    private static final int MAX_REPORT_ITEMS = 10;

    /**
     * Execute a briefing manually (synchronous, for API calls).
     * Does not change status - just executes and returns the report.
     */
    @Transactional
    public DailyReportDTO executeManually(DayBrief dayBrief) {
        // Re-fetch with sources eagerly loaded for report generation
        DayBrief refreshed = dayBriefRepository.findByIdWithSources(dayBrief.getId())
                .orElseThrow(() -> new IllegalArgumentException("DayBrief not found: " + dayBrief.getId()));
        DailyReportDTO report = doExecuteBriefing(refreshed);

        // Send email if enabled
        reportEmailDeliveryService.sendReportEmail(refreshed, report);

        return report;
    }

    /**
     * Process a briefing through the execution pipeline.
     * Updates status through lifecycle: QUEUED -> PROCESSING -> ACTIVE/ERROR
     */
    @Transactional
    public void process(UUID dayBriefId) {
        DayBrief dayBrief = dayBriefRepository.findByIdWithSources(dayBriefId).orElse(null);

        if (dayBrief == null) {
            log.warn("DayBrief not found for processing: day_brief_id={}", dayBriefId);
            return;
        }

        // Skip if not in expected status
        if (dayBrief.getStatus() != DayBriefStatus.QUEUED) {
            log.debug("Skipping day brief not in QUEUED state: day_brief_id={} status={}",
                    dayBriefId, dayBrief.getStatus());
            return;
        }

        // Mark as processing
        dayBrief.setStatus(DayBriefStatus.PROCESSING);
        dayBrief.setProcessingStartedAt(Instant.now());
        dayBriefRepository.save(dayBrief);

        try {
            DailyReportDTO report = doExecuteBriefing(dayBrief);

            // Success: mark as active (ready for next scheduled run)
            markAsActive(dayBrief);

            // Send email if enabled
            reportEmailDeliveryService.sendReportEmail(dayBrief, report);

            log.info("Processed day_brief_id={} title={}", dayBriefId, dayBrief.getTitle());

        } catch (Exception e) {
            log.error("Failed to process day_brief_id={}: {}", dayBriefId, e.getMessage(), e);
            markAsError(dayBrief, "Processing failed: " + e.getMessage());
        }
    }

    private DailyReportDTO doExecuteBriefing(DayBrief dayBrief) {
        log.info("Executing briefing {} for user {}", dayBrief.getId(), dayBrief.getUserId());

        // 1. Get source IDs for this briefing
        List<UUID> sourceIds = dayBrief.getSourceIds();
        if (sourceIds.isEmpty()) {
            log.warn("Briefing {} has no sources, creating empty report", dayBrief.getId());
            return createEmptyReport(dayBrief);
        }

        // 2. Get top scored items from linked sources since last execution
        // Items are already scored during processing (inline scoring)
        // For first run: WEEKLY briefs look back 7 days, DAILY briefs look back 1 day
        int defaultLookbackDays = dayBrief.getFrequency() == BriefingFrequency.WEEKLY ? 7 : 1;
        Instant since = dayBrief.getLastExecutedAt() != null
                ? dayBrief.getLastExecutedAt()
                : Instant.now().minus(defaultLookbackDays, ChronoUnit.DAYS);

        List<NewsItem> topScoredItems = newsItemRepository.findTopScoredItems(
                sourceIds,
                NewsItemStatus.DONE,
                since,
                PageRequest.of(0, MAX_REPORT_ITEMS)
        );

        log.info("Found {} scored news items for briefing {}", topScoredItems.size(), dayBrief.getId());

        if (topScoredItems.isEmpty()) {
            return createEmptyReport(dayBrief);
        }

        // 3. Create report with pre-scored items (no LLM calls needed)
        DailyReport report = DailyReport.builder()
                .dayBrief(dayBrief)
                .generatedAt(Instant.now())
                .status(ReportStatus.GENERATED)
                .build();
        report = dailyReportRepository.save(report);

        // 4. Create report items using pre-existing scores
        int position = 1;
        for (NewsItem item : topScoredItems) {
            ReportItem reportItem = ReportItem.builder()
                    .report(report)
                    .newsItem(item)
                    .score(item.getScore() != null ? item.getScore() : 0)
                    .position(position++)
                    .build();
            report.getItems().add(reportItem);
        }
        report = dailyReportRepository.save(report);

        // 5. Update briefing last executed
        dayBrief.setLastExecutedAt(Instant.now());
        dayBriefRepository.save(dayBrief);

        log.info("Created report {} with {} items for briefing {}",
                report.getId(), report.getItems().size(), dayBrief.getId());

        return mapToDTO(report);
    }

    private DailyReportDTO createEmptyReport(DayBrief dayBrief) {
        DailyReport report = DailyReport.builder()
                .dayBrief(dayBrief)
                .generatedAt(Instant.now())
                .status(ReportStatus.GENERATED)
                .build();
        report = dailyReportRepository.save(report);

        dayBrief.setLastExecutedAt(Instant.now());
        dayBriefRepository.save(dayBrief);

        log.info("Created empty report {} for briefing {} (no news items)",
                report.getId(), dayBrief.getId());

        return mapToDTO(report);
    }

    private void markAsActive(DayBrief dayBrief) {
        dayBrief.setStatus(DayBriefStatus.ACTIVE);
        dayBrief.setQueuedAt(null);
        dayBrief.setProcessingStartedAt(null);
        dayBrief.setErrorMessage(null);
        dayBriefRepository.save(dayBrief);
    }

    private void markAsError(DayBrief dayBrief, String message) {
        dayBrief.setStatus(DayBriefStatus.ERROR);
        dayBrief.setErrorMessage(truncate(message, 1024));
        dayBriefRepository.save(dayBrief);
        log.error("DayBrief {} marked as ERROR: {}", dayBrief.getId(), message);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private DailyReportDTO mapToDTO(DailyReport report) {
        List<ReportItemDTO> itemDTOs = report.getItems().stream()
                .map(item -> ReportItemDTO.builder()
                        .newsItemId(item.getNewsItem().getId())
                        .title(item.getNewsItem().getTitle())
                        .summary(item.getNewsItem().getSummary())
                        .link(item.getNewsItem().getLink())
                        .publishedAt(item.getNewsItem().getPublishedAt())
                        .score(item.getScore())
                        .position(item.getPosition())
                        .sourceName(item.getNewsItem().getSource().getName())
                        .build())
                .toList();

        return DailyReportDTO.builder()
                .id(report.getId())
                .dayBriefId(report.getDayBrief().getId())
                .dayBriefTitle(report.getDayBrief().getTitle())
                .dayBriefDescription(report.getDayBrief().getDescription())
                .generatedAt(report.getGeneratedAt())
                .status(report.getStatus())
                .items(itemDTOs)
                .itemCount(itemDTOs.size())
                .build();
    }
}
