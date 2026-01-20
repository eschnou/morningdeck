package be.transcode.morningdeck.server.core.search;

import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.model.Settings;
import com.meilisearch.sdk.model.TaskInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Service responsible for configuring the Meilisearch index settings.
 * Runs on application startup to ensure index is properly configured.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "meilisearch.enabled", havingValue = "true")
public class MeilisearchIndexService {

    private final Index newsItemsIndex;

    /**
     * Configure the index settings on application startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void configureIndex() {
        try {
            log.info("Configuring Meilisearch index settings...");

            Settings settings = new Settings();

            // Searchable attributes (order determines priority)
            settings.setSearchableAttributes(new String[]{
                    "title",              // Highest priority
                    "summary",
                    "tags_topics",
                    "tags_people",
                    "tags_companies",
                    "tags_technologies",
                    "author",
                    "content"             // Lowest priority (large field)
            });

            // Filterable attributes (for WHERE-like conditions)
            // CRITICAL: user_id and brief_id are required for security
            settings.setFilterableAttributes(new String[]{
                    "user_id",            // CRITICAL: tenant isolation
                    "brief_id",           // Scope to brief
                    "source_id",          // Source filter
                    "is_read",            // Read/unread filter
                    "saved",              // Saved filter
                    "score",              // Score threshold
                    "published_at",       // Date range filtering
                    "sentiment"           // Sentiment filter
            });

            // Sortable attributes
            settings.setSortableAttributes(new String[]{
                    "published_at",
                    "score",
                    "created_at"
            });

            // Ranking rules (relevance tuning)
            settings.setRankingRules(new String[]{
                    "words",              // Number of query words matched
                    "typo",               // Fewer typos = higher rank
                    "proximity",          // Words closer together = higher rank
                    "attribute",          // Match in title > match in content
                    "sort",               // User-specified sort
                    "exactness"           // Exact matches rank higher
            });

            // Apply settings
            TaskInfo taskInfo = newsItemsIndex.updateSettings(settings);
            log.info("Meilisearch index settings update queued: taskUid={}", taskInfo.getTaskUid());

        } catch (Exception e) {
            log.error("Failed to configure Meilisearch index settings", e);
            // Don't throw - allow app to start even if Meilisearch is temporarily unavailable
        }
    }

    /**
     * Get index statistics for monitoring.
     */
    public IndexStats getStats() {
        try {
            var stats = newsItemsIndex.getStats();
            return new IndexStats(
                    stats.getNumberOfDocuments(),
                    stats.isIndexing()
            );
        } catch (Exception e) {
            log.error("Failed to get index stats", e);
            return new IndexStats(0, false);
        }
    }

    public record IndexStats(long documentCount, boolean isIndexing) {}
}
