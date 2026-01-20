package be.transcode.morningdeck.server.core.service;

import be.transcode.morningdeck.server.core.dto.NewsItemDTO;
import be.transcode.morningdeck.server.core.exception.ResourceNotFoundException;
import be.transcode.morningdeck.server.core.model.NewsItem;
import be.transcode.morningdeck.server.core.model.NewsItemStatus;
import be.transcode.morningdeck.server.core.model.Source;
import be.transcode.morningdeck.server.core.repository.NewsItemRepository;
import be.transcode.morningdeck.server.core.repository.SourceRepository;
import be.transcode.morningdeck.server.core.search.MeilisearchSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsItemService {

    private final NewsItemRepository newsItemRepository;
    private final SourceRepository sourceRepository;

    // Optional: only injected when meilisearch.enabled=true
    @Autowired(required = false)
    private MeilisearchSyncService meilisearchSyncService;

    @Transactional(readOnly = true)
    public NewsItemDTO getNewsItem(UUID userId, UUID newsItemId) {
        NewsItem newsItem = getItemWithOwnershipCheck(userId, newsItemId);
        return mapToDTO(newsItem);
    }

    @Transactional
    public NewsItemDTO toggleRead(UUID userId, UUID newsItemId) {
        NewsItem item = getItemWithOwnershipCheck(userId, newsItemId);
        item.setReadAt(item.getReadAt() == null ? Instant.now() : null);
        NewsItem saved = newsItemRepository.save(item);
        syncToSearchIndex(saved);
        return mapToDTO(saved);
    }

    @Transactional
    public NewsItemDTO toggleSaved(UUID userId, UUID newsItemId) {
        NewsItem item = getItemWithOwnershipCheck(userId, newsItemId);
        item.setSaved(!item.getSaved());
        NewsItem saved = newsItemRepository.save(item);
        syncToSearchIndex(saved);
        return mapToDTO(saved);
    }

    /**
     * Sync item to Meilisearch if enabled.
     */
    private void syncToSearchIndex(NewsItem item) {
        if (meilisearchSyncService != null) {
            meilisearchSyncService.updateNewsItem(item);
        }
    }

    private NewsItem getItemWithOwnershipCheck(UUID userId, UUID newsItemId) {
        NewsItem newsItem = newsItemRepository.findById(newsItemId)
                .orElseThrow(() -> new ResourceNotFoundException("News item not found"));

        // Check ownership via source
        Source source = newsItem.getSource();
        if (!source.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("News item not found");
        }

        return newsItem;
    }

    /**
     * List items for a specific briefing with optional filters.
     * Uses a single JOIN query for efficiency.
     */
    @Transactional(readOnly = true)
    public Page<NewsItemDTO> listItemsForBriefing(
            UUID userId,
            UUID briefingId,
            UUID sourceId,
            String readStatus,
            Boolean saved,
            Integer minScore,
            Pageable pageable) {

        // Single JOIN query - no need to fetch source IDs first
        Page<NewsItem> newsItems = newsItemRepository.findByBriefIdWithFilters(
                briefingId,
                sourceId,
                readStatus,
                saved,
                minScore,
                pageable
        );

        return newsItems.map(this::mapToDTO);
    }

    /**
     * Search items for a specific briefing with text query and filters.
     * Uses a single JOIN query for efficiency.
     * Used as PostgreSQL fallback when Meilisearch is disabled.
     */
    @Transactional(readOnly = true)
    public Page<NewsItemDTO> searchItemsForBriefing(
            UUID userId,
            UUID briefingId,
            String query,
            UUID sourceId,
            String readStatus,
            Boolean saved,
            Integer minScore,
            Pageable pageable) {

        // Single JOIN query - no need to fetch source IDs first
        Page<NewsItem> newsItems = newsItemRepository.searchByBriefIdWithFilters(
                briefingId,
                sourceId,
                query.trim(),
                readStatus,
                saved,
                minScore,
                pageable
        );

        return newsItems.map(this::mapToDTO);
    }

    @Transactional(readOnly = true)
    public Page<NewsItemDTO> searchNewsItems(
            UUID userId,
            String query,
            UUID sourceId,
            Instant from,
            Instant to,
            String readStatus,
            Boolean saved,
            Pageable pageable) {

        // Get user's sources to filter by
        List<UUID> userSourceIds = sourceRepository.findSourceIdsByUserId(userId);

        if (userSourceIds.isEmpty()) {
            return Page.empty(pageable);
        }

        // If specific source requested, validate ownership
        if (sourceId != null) {
            if (!userSourceIds.contains(sourceId)) {
                throw new ResourceNotFoundException("Source not found");
            }
            userSourceIds = List.of(sourceId);
        }

        boolean hasDateFilter = from != null || to != null;
        boolean hasReadSavedFilter = readStatus != null || saved != null;
        boolean hasQuery = query != null && !query.isBlank();

        // PostgreSQL-safe date bounds
        Instant safeFrom = from != null ? from : Instant.parse("0001-01-01T00:00:00Z");
        Instant safeTo = to != null ? to : Instant.parse("9999-12-31T23:59:59Z");

        Page<NewsItem> newsItems;
        if (hasQuery && hasDateFilter) {
            newsItems = newsItemRepository.searchByQuery(userSourceIds, query.trim(), safeFrom, safeTo, pageable);
        } else if (hasQuery) {
            newsItems = newsItemRepository.searchByQueryWithoutDateFilter(userSourceIds, query.trim(), pageable);
        } else if (hasDateFilter) {
            newsItems = newsItemRepository.findBySourceIdInAndDateRange(userSourceIds, safeFrom, safeTo, pageable);
        } else if (hasReadSavedFilter) {
            newsItems = newsItemRepository.findBySourceIdsWithFilters(userSourceIds, readStatus, saved, null, pageable);
        } else {
            newsItems = newsItemRepository.findBySourceIdIn(userSourceIds, pageable);
        }

        return newsItems.map(this::mapToDTO);
    }

    private NewsItemDTO mapToDTO(NewsItem item) {
        return NewsItemDTO.builder()
                .id(item.getId())
                .title(item.getTitle())
                .link(item.getLink())
                .author(item.getAuthor())
                .publishedAt(item.getPublishedAt())
                .content(selectBestContent(item))
                .summary(item.getSummary())
                .tags(item.getTags())
                .sourceId(item.getSource().getId())
                .sourceName(item.getSource().getName())
                .readAt(item.getReadAt())
                .saved(item.getSaved())
                .score(item.getScore())
                .scoreReasoning(item.getScoreReasoning())
                .createdAt(item.getCreatedAt())
                .build();
    }

    /**
     * Returns the best available content for display.
     * Prefers fetched web content over original feed content.
     */
    private String selectBestContent(NewsItem item) {
        if (item.getWebContent() != null && !item.getWebContent().isBlank()) {
            return item.getWebContent();
        }
        return item.getCleanContent();
    }

    /**
     * Updates a news item's status in a separate transaction.
     * This ensures the status change is immediately visible to other threads/transactions.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatus(UUID newsItemId, NewsItemStatus status) {
        newsItemRepository.findById(newsItemId).ifPresent(item -> {
            item.setStatus(status);
            newsItemRepository.save(item);
        });
    }

    /**
     * Mark all unread items in a source as read.
     * Returns the count of items updated.
     */
    @Transactional
    public int markAllAsReadBySource(UUID userId, UUID sourceId) {
        // Validate source ownership
        Source source = sourceRepository.findById(sourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Source not found"));

        if (!source.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Source not found");
        }

        Instant now = Instant.now();
        return newsItemRepository.markAllAsReadBySourceId(sourceId, now, now);
    }

    /**
     * Mark all unread items in a briefing as read.
     * Returns the count of items updated.
     */
    @Transactional
    public int markAllAsReadByBriefing(UUID briefingId) {
        List<UUID> sourceIds = sourceRepository.findSourceIdsByBriefingId(briefingId);

        if (sourceIds.isEmpty()) {
            return 0;
        }

        Instant now = Instant.now();
        return newsItemRepository.markAllAsReadBySourceIds(sourceIds, now, now);
    }
}
