package be.transcode.morningdeck.server.core.job;

import be.transcode.morningdeck.server.core.model.NewsItem;
import be.transcode.morningdeck.server.core.model.NewsItemStatus;
import be.transcode.morningdeck.server.core.queue.ProcessingQueue;
import be.transcode.morningdeck.server.core.repository.NewsItemRepository;
import be.transcode.morningdeck.server.core.service.NewsItemService;
import be.transcode.morningdeck.server.core.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Scheduler job that finds news items due for AI processing and enqueues them.
 * Runs every minute (configurable) and queues items in NEW status.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "application.jobs.news-processing", name = "enabled", havingValue = "true")
public class ProcessingSchedulerJob {

    private final NewsItemRepository newsItemRepository;
    private final NewsItemService newsItemService;
    private final ProcessingQueue processingQueue;
    private final SubscriptionService subscriptionService;

    @Value("${application.jobs.news-processing.batch-size:50}")
    private int batchSize;

    /**
     * Scheduled job that runs every minute to find and enqueue news items for processing.
     * Only items in NEW status are eligible.
     */
    @Scheduled(fixedRateString = "${application.jobs.news-processing.interval:60000}")
    @Transactional(readOnly = true)
    public void scheduleProcessing() {
        if (!processingQueue.canAccept()) {
            log.warn("Queue full, skipping scheduling cycle. queue_size={}", processingQueue.size());
            return;
        }

        // Get users with available credits
        Set<UUID> usersWithCredits = subscriptionService.getUserIdsWithCredits();
        if (usersWithCredits.isEmpty()) {
            log.debug("No users with credits available");
            return;
        }

        // Find items eligible for processing (status = NEW)
        List<NewsItem> candidates = newsItemRepository.findItemsForProcessing(
                PageRequest.of(0, batchSize * 2)); // Fetch more to account for credit filtering

        // Filter to only items from users with credits
        List<NewsItem> items = candidates.stream()
                .filter(item -> usersWithCredits.contains(item.getSource().getDayBrief().getUserId()))
                .limit(batchSize)
                .toList();

        if (items.isEmpty()) {
            log.debug("No news items due for processing");
            return;
        }

        log.info("Found {} news items for processing", items.size());

        int enqueuedCount = 0;
        for (NewsItem item : items) {
            if (!processingQueue.canAccept()) {
                log.warn("Queue full during scheduling, stopping. enqueued={}", enqueuedCount);
                break;
            }

            // Mark as pending in separate transaction (commits immediately)
            newsItemService.updateStatus(item.getId(), NewsItemStatus.PENDING);

            boolean success = processingQueue.enqueue(item.getId());
            if (success) {
                enqueuedCount++;
            } else {
                // Rollback status if enqueue failed
                newsItemService.updateStatus(item.getId(), NewsItemStatus.NEW);
                log.warn("Failed to enqueue news_item_id={}", item.getId());
            }
        }

        log.info("Scheduled {} news items for processing", enqueuedCount);
    }
}
