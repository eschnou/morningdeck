package be.transcode.morningdeck.server.core.search;

import be.transcode.morningdeck.server.core.model.NewsItem;
import be.transcode.morningdeck.server.core.repository.NewsItemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.model.TaskInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for synchronizing NewsItem data with Meilisearch index.
 * All operations are async to avoid blocking the main application flow.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "meilisearch.enabled", havingValue = "true")
public class MeilisearchSyncService {

    private final Index newsItemsIndex;
    private final NewsItemRepository newsItemRepository;
    private final ObjectMapper objectMapper;

    /**
     * Index a single NewsItem.
     * Called when a new item is created or needs to be indexed.
     */
    @Async
    public void indexNewsItem(NewsItem item) {
        try {
            NewsItemSearchDocument doc = NewsItemSearchDocument.from(item);
            String json = objectMapper.writeValueAsString(List.of(doc));

            TaskInfo taskInfo = newsItemsIndex.addDocuments(json, "id");
            log.debug("Indexed news item: id={}, taskUid={}", item.getId(), taskInfo.getTaskUid());
        } catch (Exception e) {
            log.error("Failed to index news item: id={}", item.getId(), e);
        }
    }

    /**
     * Update a NewsItem in the index.
     * Called when item fields change (e.g., read status, saved, score).
     */
    @Async
    public void updateNewsItem(NewsItem item) {
        try {
            NewsItemSearchDocument doc = NewsItemSearchDocument.from(item);
            String json = objectMapper.writeValueAsString(List.of(doc));

            TaskInfo taskInfo = newsItemsIndex.updateDocuments(json, "id");
            log.debug("Updated news item in index: id={}, taskUid={}", item.getId(), taskInfo.getTaskUid());
        } catch (Exception e) {
            log.error("Failed to update news item in index: id={}", item.getId(), e);
        }
    }

    /**
     * Delete a NewsItem from the index.
     */
    @Async
    public void deleteNewsItem(UUID id) {
        try {
            TaskInfo taskInfo = newsItemsIndex.deleteDocument(id.toString());
            log.debug("Deleted news item from index: id={}, taskUid={}", id, taskInfo.getTaskUid());
        } catch (Exception e) {
            log.error("Failed to delete news item from index: id={}", id, e);
        }
    }

    /**
     * Reindex all items for a specific brief.
     * Useful when syncing a brief's data or recovering from issues.
     */
    public void reindexBrief(UUID briefId) {
        log.info("Starting reindex for brief: {}", briefId);

        int page = 0;
        int pageSize = 100;
        int totalIndexed = 0;

        while (true) {
            Page<NewsItem> items = newsItemRepository.findByBriefId(briefId, PageRequest.of(page, pageSize));

            if (items.isEmpty()) {
                break;
            }

            List<NewsItemSearchDocument> docs = items.stream()
                    .map(NewsItemSearchDocument::from)
                    .collect(Collectors.toList());

            try {
                String json = objectMapper.writeValueAsString(docs);
                newsItemsIndex.addDocuments(json, "id");
                totalIndexed += docs.size();
            } catch (Exception e) {
                log.error("Failed to batch index items for brief: {}, page: {}", briefId, page, e);
            }

            if (!items.hasNext()) {
                break;
            }
            page++;
        }

        log.info("Completed reindex for brief: {}, totalIndexed={}", briefId, totalIndexed);
    }

    /**
     * Full reindex of all items.
     * Should be used sparingly, primarily for initial setup or disaster recovery.
     */
    public void reindexAll() {
        log.info("Starting full reindex of all news items");

        int page = 0;
        int pageSize = 100;
        int totalIndexed = 0;

        while (true) {
            Page<NewsItem> items = newsItemRepository.findAllWithSourceAndBrief(PageRequest.of(page, pageSize));

            if (items.isEmpty()) {
                break;
            }

            List<NewsItemSearchDocument> docs = items.stream()
                    .map(item -> {
                        try {
                            return NewsItemSearchDocument.from(item);
                        } catch (Exception e) {
                            log.warn("Skipping item without proper relationships: {}", item.getId());
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (!docs.isEmpty()) {
                try {
                    String json = objectMapper.writeValueAsString(docs);
                    newsItemsIndex.addDocuments(json, "id");
                    totalIndexed += docs.size();
                } catch (Exception e) {
                    log.error("Failed to batch index items, page: {}", page, e);
                }
            }

            if (!items.hasNext()) {
                break;
            }
            page++;

            // Log progress every 10 pages
            if (page % 10 == 0) {
                log.info("Reindex progress: {} items indexed", totalIndexed);
            }
        }

        log.info("Completed full reindex: totalIndexed={}", totalIndexed);
    }

    /**
     * Delete all items for a user from the index.
     * Used when a user is deleted.
     */
    public void deleteByUserId(UUID userId) {
        try {
            String filter = "user_id = '" + userId.toString() + "'";
            TaskInfo taskInfo = newsItemsIndex.deleteDocumentsByFilter(filter);
            log.info("Deleted all items for user: userId={}, taskUid={}", userId, taskInfo.getTaskUid());
        } catch (Exception e) {
            log.error("Failed to delete items for user: userId={}", userId, e);
        }
    }

    /**
     * Delete all items for a brief from the index.
     * Used when a brief is deleted.
     */
    public void deleteByBriefId(UUID briefId) {
        try {
            String filter = "brief_id = '" + briefId.toString() + "'";
            TaskInfo taskInfo = newsItemsIndex.deleteDocumentsByFilter(filter);
            log.info("Deleted all items for brief: briefId={}, taskUid={}", briefId, taskInfo.getTaskUid());
        } catch (Exception e) {
            log.error("Failed to delete items for brief: briefId={}", briefId, e);
        }
    }
}
