package be.transcode.morningdeck.server.core.queue;

import be.transcode.morningdeck.server.core.exception.InsufficientCreditsException;
import be.transcode.morningdeck.server.core.model.NewsItem;
import be.transcode.morningdeck.server.core.model.NewsItemStatus;
import be.transcode.morningdeck.server.core.model.NewsItemTags;
import be.transcode.morningdeck.server.core.repository.NewsItemRepository;
import be.transcode.morningdeck.server.core.search.MeilisearchSyncService;
import be.transcode.morningdeck.server.core.service.SubscriptionService;
import be.transcode.morningdeck.server.provider.ai.AiService;
import be.transcode.morningdeck.server.provider.ai.AiUsageContext;
import be.transcode.morningdeck.server.provider.ai.model.EnrichmentWithScoreResult;
import be.transcode.morningdeck.server.provider.webfetch.WebContentFetcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Worker component that processes individual news item AI jobs.
 * Handles the full pipeline: PENDING -> PROCESSING -> DONE/ERROR
 * Optionally fetches web content before AI enrichment.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessingWorker {

    private static final int CONTENT_LENGTH_THRESHOLD = 2000;

    private final NewsItemRepository newsItemRepository;
    private final AiService aiService;
    private final WebContentFetcher webContentFetcher;
    private final SubscriptionService subscriptionService;

    // Optional: only injected when meilisearch.enabled=true
    @Autowired(required = false)
    private MeilisearchSyncService meilisearchSyncService;

    /**
     * Process a news item through the full AI pipeline.
     * Updates status through lifecycle: PENDING -> PROCESSING -> DONE/ERROR
     */
    @Transactional
    public void process(UUID newsItemId) {
        NewsItem item = newsItemRepository.findById(newsItemId).orElse(null);

        if (item == null) {
            log.warn("NewsItem not found for processing: news_item_id={}", newsItemId);
            return;
        }

        // Skip if not in expected status
        if (item.getStatus() != NewsItemStatus.PENDING) {
            log.debug("Skipping news item not in PENDING state: news_item_id={} status={}",
                    newsItemId, item.getStatus());
            return;
        }

        // Mark as processing
        item.setStatus(NewsItemStatus.PROCESSING);
        newsItemRepository.save(item);

        try {
            doProcess(item);

            // Success: mark as done
            item.setStatus(NewsItemStatus.DONE);
            NewsItem saved = newsItemRepository.save(item);

            // Index in Meilisearch if enabled
            indexInMeilisearch(saved);

            log.info("Processed news_item_id={} title={}", newsItemId, item.getTitle());

        } catch (Exception e) {
            log.error("Failed to process news_item_id={}: {}", newsItemId, e.getMessage(), e);
            markAsError(item, "Processing failed: " + e.getMessage());
        }
    }

    private void doProcess(NewsItem item) {
        String content = item.getCleanContent() != null ? item.getCleanContent() : item.getRawContent();
        String webContent = null;

        // Fetch web content if applicable
        if (shouldFetchWebContent(item)) {
            log.debug("Fetching web content for news_item_id={} url={}", item.getId(), item.getLink());
            webContent = webContentFetcher.fetch(item.getLink()).orElse(null);
            if (webContent != null) {
                item.setWebContent(webContent);
                log.debug("Web content fetched for news_item_id={} ({} chars)", item.getId(), webContent.length());
            }
        }

        // Get briefing criteria from the item's source's dayBrief
        String briefingCriteria = item.getSource().getDayBrief().getBriefing();

        // Set user context for usage tracking
        UUID userId = item.getSource().getDayBrief().getUserId();
        try {
            AiUsageContext.setUserId(userId);

            // Enrich and score in a single LLM call
            log.debug("Enriching and scoring news_item_id={}", item.getId());
            EnrichmentWithScoreResult result = aiService.enrichWithScore(item.getTitle(), content, webContent, briefingCriteria);

            item.setSummary(result.summary());
            NewsItemTags tags = NewsItemTags.builder()
                    .topics(result.topics())
                    .people(result.entities().people())
                    .companies(result.entities().companies())
                    .technologies(result.entities().technologies())
                    .sentiment(result.sentiment())
                    .build();
            item.setTags(tags);

            // Set score from the combined result
            item.setScore(result.score());
            item.setScoreReasoning(result.scoreReasoning());

            // Deduct credit after successful processing
            boolean deducted = subscriptionService.useCredits(userId, 1);
            if (!deducted) {
                log.error("Failed to deduct credit after AI processing: userId={}, itemId={}", userId, item.getId());
                throw new InsufficientCreditsException(userId, 1, 0);
            }
            log.debug("Deducted 1 credit for news_item_id={} userId={}", item.getId(), userId);
        } finally {
            AiUsageContext.clear();
        }
    }

    /**
     * Determines if web content should be fetched for the news item.
     * Skips fetch if: no link, non-HTTP link, or content is already substantial.
     */
    boolean shouldFetchWebContent(NewsItem item) {
        String link = item.getLink();

        // Skip if no link or non-HTTP
        if (link == null || (!link.startsWith("http://") && !link.startsWith("https://"))) {
            log.debug("Skipping web fetch for news_item_id={}: invalid or non-HTTP link", item.getId());
            return false;
        }

        // Skip if content is already substantial
        String content = item.getCleanContent() != null ? item.getCleanContent() : item.getRawContent();
        if (content != null && content.length() > CONTENT_LENGTH_THRESHOLD) {
            log.debug("Skipping web fetch for news_item_id={}: content already substantial ({} chars)",
                    item.getId(), content.length());
            return false;
        }

        return true;
    }

    private void markAsError(NewsItem item, String message) {
        item.setStatus(NewsItemStatus.ERROR);
        item.setErrorMessage(truncate(message, 1024));
        newsItemRepository.save(item);
        log.error("NewsItem {} marked as ERROR: {}", item.getId(), message);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    /**
     * Index the processed item in Meilisearch if the service is enabled.
     */
    private void indexInMeilisearch(NewsItem item) {
        if (meilisearchSyncService != null) {
            meilisearchSyncService.indexNewsItem(item);
        }
    }
}
