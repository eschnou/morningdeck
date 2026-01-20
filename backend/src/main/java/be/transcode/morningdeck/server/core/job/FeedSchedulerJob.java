package be.transcode.morningdeck.server.core.job;

import be.transcode.morningdeck.server.core.model.FetchStatus;
import be.transcode.morningdeck.server.core.model.Source;
import be.transcode.morningdeck.server.core.queue.FetchQueue;
import be.transcode.morningdeck.server.core.repository.SourceRepository;
import be.transcode.morningdeck.server.core.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Scheduler job that finds sources due for refresh and enqueues them.
 * Runs every minute (configurable) and checks which sources need to be fetched
 * based on their last fetch time and per-source refresh interval.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "application.jobs.feed-ingestion", name = "enabled", havingValue = "true")
public class FeedSchedulerJob {

    private final SourceRepository sourceRepository;
    private final FetchQueue fetchQueue;
    private final SubscriptionService subscriptionService;

    @Value("${application.jobs.feed-ingestion.batch-size:100}")
    private int batchSize;

    /**
     * Scheduled job that runs every minute to find and enqueue sources due for refresh.
     * New sources (with null lastFetchedAt) are prioritized via NULLS FIRST ordering.
     * Each source's individual refreshIntervalMinutes is checked.
     */
    @Scheduled(fixedRateString = "${application.jobs.feed-scheduling.interval:60000}")
    @Transactional
    public void scheduleFeeds() {
        if (!fetchQueue.canAccept()) {
            log.warn("Queue full, skipping scheduling cycle. queue_size={}", fetchQueue.size());
            return;
        }

        // Get sources that might be due (using max possible interval as cutoff)
        // We'll filter by individual refresh intervals in Java for database portability
        Instant maxCutoff = Instant.now().minusSeconds(60); // At least 1 minute old
        List<Source> candidates = sourceRepository.findSourcesDueForRefreshSince(
                maxCutoff, PageRequest.of(0, batchSize * 2));

        // Get users with available credits for efficient filtering
        Set<UUID> usersWithCredits = subscriptionService.getUserIdsWithCredits();

        // Filter by each source's individual refresh interval and user credits
        Instant now = Instant.now();
        List<Source> dueForRefresh = candidates.stream()
                .filter(s -> isDueForRefresh(s, now))
                .filter(s -> usersWithCredits.contains(s.getDayBrief().getUserId()))
                .limit(batchSize)
                .toList();

        // Log sources skipped due to credits
        long skippedDueToCredits = candidates.stream()
                .filter(s -> isDueForRefresh(s, now))
                .filter(s -> !usersWithCredits.contains(s.getDayBrief().getUserId()))
                .count();
        if (skippedDueToCredits > 0) {
            log.info("Skipped {} sources due to insufficient credits", skippedDueToCredits);
        }

        if (dueForRefresh.isEmpty()) {
            log.debug("No sources due for refresh");
            return;
        }

        log.info("Found {} sources due for refresh", dueForRefresh.size());

        int enqueuedCount = 0;
        for (Source source : dueForRefresh) {
            // Double-check queue can still accept
            if (!fetchQueue.canAccept()) {
                log.warn("Queue full during scheduling, stopping. enqueued={}", enqueuedCount);
                break;
            }

            // Mark as queued and enqueue
            source.setFetchStatus(FetchStatus.QUEUED);
            source.setQueuedAt(Instant.now());
            sourceRepository.save(source);

            boolean success = fetchQueue.enqueue(source.getId());
            if (success) {
                enqueuedCount++;
            } else {
                // Rollback status if enqueue failed
                source.setFetchStatus(FetchStatus.IDLE);
                source.setQueuedAt(null);
                sourceRepository.save(source);
                log.warn("Failed to enqueue source_id={}", source.getId());
            }
        }

        log.info("Scheduled {} sources for fetch", enqueuedCount);
    }

    /**
     * Check if a source is due for refresh based on its individual refresh interval.
     */
    private boolean isDueForRefresh(Source source, Instant now) {
        // Never fetched - always due
        if (source.getLastFetchedAt() == null) {
            return true;
        }

        int intervalMinutes = source.getRefreshIntervalMinutes() != null
                ? source.getRefreshIntervalMinutes()
                : 15; // Default 15 minutes

        Instant dueAt = source.getLastFetchedAt().plus(Duration.ofMinutes(intervalMinutes));
        return now.isAfter(dueAt);
    }
}
