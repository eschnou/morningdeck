package be.transcode.morningdeck.server.core.queue;

import be.transcode.morningdeck.server.core.model.FetchStatus;
import be.transcode.morningdeck.server.core.model.NewsItem;
import be.transcode.morningdeck.server.core.model.NewsItemStatus;
import be.transcode.morningdeck.server.core.model.Source;
import be.transcode.morningdeck.server.core.model.SourceStatus;
import be.transcode.morningdeck.server.core.repository.NewsItemRepository;
import be.transcode.morningdeck.server.core.repository.SourceRepository;
import be.transcode.morningdeck.server.provider.sourcefetch.SourceFetcher;
import be.transcode.morningdeck.server.provider.sourcefetch.model.FetchedItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Worker component that processes individual source fetch jobs.
 * Handles the actual RSS fetching and news item persistence.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FetchWorker {

    private final SourceRepository sourceRepository;
    private final NewsItemRepository newsItemRepository;
    private final List<SourceFetcher> sourceFetchers;

    /**
     * Process a source fetch job.
     * Updates fetch status through lifecycle: QUEUED -> FETCHING -> IDLE
     */
    @Transactional
    public void process(UUID sourceId) {
        Source source = sourceRepository.findById(sourceId).orElse(null);

        if (source == null) {
            log.warn("Source not found for fetch: source_id={}", sourceId);
            return;
        }

        // Skip if source is not in expected state
        if (source.getStatus() != SourceStatus.ACTIVE) {
            log.debug("Skipping non-active source: source_id={} status={}",
                    sourceId, source.getStatus());
            resetToIdle(source);
            return;
        }

        // Mark as fetching
        source.setFetchStatus(FetchStatus.FETCHING);
        source.setFetchStartedAt(Instant.now());
        sourceRepository.save(source);

        try {
            int newItemCount = doFetch(source);

            // Success: reset to idle
            source.setFetchStatus(FetchStatus.IDLE);
            source.setLastFetchedAt(Instant.now());
            source.setLastError(null);
            source.setQueuedAt(null);
            source.setFetchStartedAt(null);

            // Clear error status if previously in error
            if (source.getStatus() == SourceStatus.ERROR) {
                source.setStatus(SourceStatus.ACTIVE);
            }

            sourceRepository.save(source);
            log.info("Fetched source_id={} name={} new_items={}",
                    sourceId, source.getName(), newItemCount);

        } catch (Exception e) {
            log.error("Failed to fetch source_id={}: {}", sourceId, e.getMessage(), e);
            handleFetchError(source, e);
        }
    }

    private int doFetch(Source source) {
        SourceFetcher fetcher = findFetcher(source);

        // First import: lastFetchedAt is null - skip AI processing for existing items
        boolean isFirstImport = source.getLastFetchedAt() == null;

        List<FetchedItem> items = fetcher.fetch(source, source.getLastFetchedAt());

        int newItemCount = 0;
        for (FetchedItem item : items) {
            if (!newsItemRepository.existsBySourceIdAndGuid(source.getId(), item.getGuid())) {
                NewsItem newsItem = mapToNewsItem(item, source, isFirstImport);
                newsItemRepository.save(newsItem);
                newItemCount++;
            }
        }

        if (isFirstImport && newItemCount > 0) {
            log.info("First import for source_id={}: marked {} items as DONE (skipping AI processing)",
                    source.getId(), newItemCount);
        }

        return newItemCount;
    }

    private SourceFetcher findFetcher(Source source) {
        return sourceFetchers.stream()
                .filter(f -> f.getSourceType() == source.getType())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No fetcher for source type: " + source.getType()));
    }

    private NewsItem mapToNewsItem(FetchedItem item, Source source, boolean skipProcessing) {
        // On first import, mark as DONE to skip AI processing
        // On subsequent fetches, mark as NEW for normal processing pipeline
        NewsItemStatus status = skipProcessing ? NewsItemStatus.DONE : NewsItemStatus.NEW;

        return NewsItem.builder()
                .source(source)
                .guid(item.getGuid())
                .title(item.getTitle())
                .link(item.getLink())
                .author(item.getAuthor())
                .publishedAt(item.getPublishedAt())
                .rawContent(item.getRawContent())
                .cleanContent(item.getCleanContent())
                .status(status)
                .build();
    }

    private void handleFetchError(Source source, Exception e) {
        source.setFetchStatus(FetchStatus.IDLE);
        source.setStatus(SourceStatus.ERROR);
        source.setLastError(truncate(e.getMessage(), 1024));
        source.setQueuedAt(null);
        source.setFetchStartedAt(null);
        sourceRepository.save(source);
    }

    private void resetToIdle(Source source) {
        source.setFetchStatus(FetchStatus.IDLE);
        source.setQueuedAt(null);
        source.setFetchStartedAt(null);
        sourceRepository.save(source);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
