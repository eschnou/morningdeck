package be.transcode.morningdeck.server.core.controller;

import be.transcode.morningdeck.server.core.dto.BulkUpdateResult;
import be.transcode.morningdeck.server.core.dto.DailyReportDTO;
import be.transcode.morningdeck.server.core.dto.DayBriefDTO;
import be.transcode.morningdeck.server.core.dto.NewsItemDTO;
import be.transcode.morningdeck.server.core.dto.ReorderBriefsRequest;
import be.transcode.morningdeck.server.core.dto.SourceDTO;
import be.transcode.morningdeck.server.core.exception.InsufficientCreditsException;
import be.transcode.morningdeck.server.core.model.DayBrief;
import be.transcode.morningdeck.server.core.model.SourceStatus;
import be.transcode.morningdeck.server.core.model.SourceType;
import be.transcode.morningdeck.server.core.queue.BriefingWorker;
import be.transcode.morningdeck.server.core.model.DayBriefStatus;
import be.transcode.morningdeck.server.core.model.User;
import be.transcode.morningdeck.server.core.search.ArticleSearchRequest;
import be.transcode.morningdeck.server.core.search.MeilisearchSearchService;
import be.transcode.morningdeck.server.core.service.DayBriefService;
import be.transcode.morningdeck.server.core.service.NewsItemService;
import be.transcode.morningdeck.server.core.service.ReportService;
import be.transcode.morningdeck.server.core.service.SourceService;
import be.transcode.morningdeck.server.core.service.SubscriptionService;
import be.transcode.morningdeck.server.core.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/daybriefs")
@RequiredArgsConstructor
public class DayBriefController {

    private final DayBriefService dayBriefService;
    private final SourceService sourceService;
    private final NewsItemService newsItemService;
    private final ReportService reportService;
    private final BriefingWorker briefingWorker;
    private final UserService userService;
    private final SubscriptionService subscriptionService;

    // Optional: only injected when meilisearch.enabled=true
    @Autowired(required = false)
    private MeilisearchSearchService meilisearchSearchService;

    @PostMapping
    public ResponseEntity<DayBriefDTO> createDayBrief(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody DayBriefDTO request) {

        User user = userService.getInternalUserByUsername(userDetails.getUsername());

        DayBriefDTO dayBrief = dayBriefService.createDayBrief(
                user.getId(),
                request.getTitle(),
                request.getDescription(),
                request.getBriefing(),
                request.getFrequency(),
                request.getScheduleDayOfWeek(),
                request.getScheduleTime(),
                request.getTimezone(),
                request.getEmailDeliveryEnabled()
        );

        return ResponseEntity.ok(dayBrief);
    }

    @GetMapping
    public ResponseEntity<Page<DayBriefDTO>> listDayBriefs(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) DayBriefStatus status,
            @PageableDefault(size = 20) Pageable pageable) {

        User user = userService.getInternalUserByUsername(userDetails.getUsername());
        Page<DayBriefDTO> dayBriefs = dayBriefService.listDayBriefs(user.getId(), status, pageable);

        return ResponseEntity.ok(dayBriefs);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DayBriefDTO> getDayBrief(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {

        User user = userService.getInternalUserByUsername(userDetails.getUsername());
        DayBriefDTO dayBrief = dayBriefService.getDayBrief(user.getId(), id);

        return ResponseEntity.ok(dayBrief);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DayBriefDTO> updateDayBrief(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @RequestBody DayBriefDTO request) {

        User user = userService.getInternalUserByUsername(userDetails.getUsername());

        DayBriefDTO dayBrief = dayBriefService.updateDayBrief(
                user.getId(),
                id,
                request.getTitle(),
                request.getDescription(),
                request.getBriefing(),
                request.getFrequency(),
                request.getScheduleDayOfWeek(),
                request.getScheduleTime(),
                request.getTimezone(),
                request.getStatus(),
                request.getEmailDeliveryEnabled()
        );

        return ResponseEntity.ok(dayBrief);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDayBrief(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {

        User user = userService.getInternalUserByUsername(userDetails.getUsername());
        dayBriefService.deleteDayBrief(user.getId(), id);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reorder")
    public ResponseEntity<Void> reorderBriefs(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ReorderBriefsRequest request) {

        User user = userService.getInternalUserByUsername(userDetails.getUsername());
        dayBriefService.reorderBriefs(user.getId(), request.getBriefIds());

        return ResponseEntity.noContent().build();
    }

    // Briefing-scoped source endpoints

    @GetMapping("/{id}/sources")
    public ResponseEntity<Page<SourceDTO>> listBriefingSources(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @RequestParam(required = false) SourceStatus status,
            @PageableDefault(size = 20) Pageable pageable) {

        User user = userService.getInternalUserByUsername(userDetails.getUsername());
        Page<SourceDTO> sources = sourceService.listSourcesForBriefing(user.getId(), id, status, pageable);

        return ResponseEntity.ok(sources);
    }

    @PostMapping("/{id}/sources")
    public ResponseEntity<SourceDTO> createBriefingSource(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody SourceDTO request) {

        User user = userService.getInternalUserByUsername(userDetails.getUsername());

        // Default to RSS if type not specified
        SourceType type = request.getType() != null ? request.getType() : SourceType.RSS;

        SourceDTO source = sourceService.createSource(
                user.getId(),
                id,
                request.getUrl(),
                request.getName(),
                type,
                request.getTags(),
                request.getRefreshIntervalMinutes(),
                request.getExtractionPrompt()
        );

        return ResponseEntity.ok(source);
    }

    // Briefing-scoped items endpoint

    @GetMapping("/{id}/items")
    public ResponseEntity<Page<NewsItemDTO>> listBriefingItems(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID sourceId,
            @RequestParam(required = false) String readStatus,
            @RequestParam(required = false) Boolean saved,
            @RequestParam(required = false) Integer minScore,
            @PageableDefault(size = 20) Pageable pageable) {

        User user = userService.getInternalUserByUsername(userDetails.getUsername());

        // Validate user owns this briefing
        dayBriefService.getDayBrief(user.getId(), id);

        // Handle search queries
        if (q != null && !q.isBlank()) {
            // Use Meilisearch when available
            if (meilisearchSearchService != null) {
                ArticleSearchRequest searchRequest = ArticleSearchRequest.builder()
                        .query(q.trim())
                        .userId(user.getId())
                        .briefId(id)
                        .sourceId(sourceId)
                        .readStatus(readStatus)
                        .saved(saved)
                        .minScore(minScore)
                        .page(pageable.getPageNumber())
                        .size(pageable.getPageSize())
                        .build();

                Page<NewsItemDTO> items = meilisearchSearchService.search(searchRequest);
                return ResponseEntity.ok(items);
            }

            // PostgreSQL fallback when Meilisearch is disabled
            Page<NewsItemDTO> items = newsItemService.searchItemsForBriefing(
                    user.getId(), id, q, sourceId, readStatus, saved, minScore, pageable);
            return ResponseEntity.ok(items);
        }

        // Default: standard listing (no search query)
        Page<NewsItemDTO> items = newsItemService.listItemsForBriefing(
                user.getId(), id, sourceId, readStatus, saved, minScore, pageable);

        return ResponseEntity.ok(items);
    }

    // Report endpoints

    @GetMapping("/{id}/reports")
    public ResponseEntity<Page<DailyReportDTO>> listReports(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @PageableDefault(size = 20) Pageable pageable) {

        User user = userService.getInternalUserByUsername(userDetails.getUsername());
        Page<DailyReportDTO> reports = reportService.listReports(user.getId(), id, pageable);

        return ResponseEntity.ok(reports);
    }

    @GetMapping("/{id}/reports/{reportId}")
    public ResponseEntity<DailyReportDTO> getReport(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @PathVariable UUID reportId) {

        User user = userService.getInternalUserByUsername(userDetails.getUsername());
        DailyReportDTO report = reportService.getReport(user.getId(), id, reportId);

        return ResponseEntity.ok(report);
    }

    @DeleteMapping("/{id}/reports/{reportId}")
    public ResponseEntity<Void> deleteReport(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @PathVariable UUID reportId) {

        User user = userService.getInternalUserByUsername(userDetails.getUsername());
        reportService.deleteReport(user.getId(), id, reportId);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/execute")
    public ResponseEntity<DailyReportDTO> executeBriefing(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {

        User user = userService.getInternalUserByUsername(userDetails.getUsername());

        // Check credits before manual execution
        if (!subscriptionService.hasCredits(user.getId())) {
            throw new InsufficientCreditsException(user.getId(), 1, 0);
        }

        DayBrief dayBrief = dayBriefService.getDayBriefEntity(user.getId(), id);
        DailyReportDTO report = briefingWorker.executeManually(dayBrief);

        return ResponseEntity.ok(report);
    }

    @PostMapping("/{id}/mark-all-read")
    public ResponseEntity<BulkUpdateResult> markAllAsRead(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {

        User user = userService.getInternalUserByUsername(userDetails.getUsername());

        // Validate user owns this briefing
        dayBriefService.getDayBrief(user.getId(), id);

        int count = newsItemService.markAllAsReadByBriefing(id);

        return ResponseEntity.ok(BulkUpdateResult.builder()
                .updatedCount(count)
                .build());
    }
}
