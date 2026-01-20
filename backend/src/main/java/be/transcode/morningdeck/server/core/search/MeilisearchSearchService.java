package be.transcode.morningdeck.server.core.search;

import be.transcode.morningdeck.server.core.dto.NewsItemDTO;
import be.transcode.morningdeck.server.core.model.NewsItem;
import be.transcode.morningdeck.server.core.repository.NewsItemRepository;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.model.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for searching articles via Meilisearch.
 * Only active when meilisearch.enabled=true.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "meilisearch.enabled", havingValue = "true")
public class MeilisearchSearchService {

    private final Index newsItemsIndex;
    private final NewsItemRepository newsItemRepository;

    /**
     * Search articles using Meilisearch.
     * Returns a page of NewsItemDTO with full entity data from the database.
     */
    public Page<NewsItemDTO> search(ArticleSearchRequest request) {
        if (request.getUserId() == null || request.getBriefId() == null) {
            throw new IllegalArgumentException("userId and briefId are required for search");
        }

        try {
            // Build the Meilisearch search request
            SearchRequest meiliRequest = buildSearchRequest(request);

            // Execute search - using offset/limit returns SearchResult
            SearchResult result = (SearchResult) newsItemsIndex.search(meiliRequest);

            // Extract IDs from search results
            List<UUID> ids = extractIds(result);

            if (ids.isEmpty()) {
                return Page.empty(PageRequest.of(request.getPage(), request.getSize()));
            }

            // Fetch full entities from database with source eagerly loaded
            List<NewsItem> items = newsItemRepository.findAllByIdWithSource(ids);

            // Create ID -> Entity map for ordering
            Map<UUID, NewsItem> itemMap = items.stream()
                    .collect(Collectors.toMap(NewsItem::getId, item -> item));

            // Map to DTOs in search result order
            List<NewsItemDTO> dtos = ids.stream()
                    .map(itemMap::get)
                    .filter(Objects::nonNull)
                    .map(this::toDTO)
                    .collect(Collectors.toList());

            // Build page response - estimatedTotalHits is used when offset/limit pagination
            Integer estimatedTotal = result.getEstimatedTotalHits();
            long totalHits = estimatedTotal != null ? estimatedTotal : dtos.size();
            return new PageImpl<>(dtos, PageRequest.of(request.getPage(), request.getSize()), totalHits);

        } catch (Exception e) {
            log.error("Meilisearch search failed: query={}, briefId={}",
                    request.getQuery(), request.getBriefId(), e);
            throw new SearchException("Search failed", e);
        }
    }

    private SearchRequest buildSearchRequest(ArticleSearchRequest request) {
        return SearchRequest.builder()
                .q(request.getQuery())
                .filter(new String[]{buildFilterString(request)})
                .sort(new String[]{"published_at:desc"})
                .offset(request.getPage() * request.getSize())
                .limit(request.getSize())
                .build();
    }

    /**
     * Build the filter string for Meilisearch.
     * CRITICAL: Always includes user_id and brief_id for security.
     */
    private String buildFilterString(ArticleSearchRequest request) {
        List<String> filters = new ArrayList<>();

        // CRITICAL: Always filter by user for security
        filters.add("user_id = '" + request.getUserId().toString() + "'");

        // Scope to brief
        filters.add("brief_id = '" + request.getBriefId().toString() + "'");

        // Optional filters
        if (request.getSourceId() != null) {
            filters.add("source_id = '" + request.getSourceId().toString() + "'");
        }

        if (request.getReadStatus() != null) {
            boolean isRead = "READ".equalsIgnoreCase(request.getReadStatus());
            filters.add("is_read = " + isRead);
        }

        if (request.getSaved() != null) {
            filters.add("saved = " + request.getSaved());
        }

        if (request.getMinScore() != null) {
            filters.add("score >= " + request.getMinScore());
        }

        return String.join(" AND ", filters);
    }

    @SuppressWarnings("unchecked")
    private List<UUID> extractIds(SearchResult result) {
        if (result.getHits() == null) {
            return Collections.emptyList();
        }

        return result.getHits().stream()
                .map(hit -> {
                    if (hit instanceof Map) {
                        Object id = ((Map<String, Object>) hit).get("id");
                        if (id != null) {
                            return UUID.fromString(id.toString());
                        }
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private NewsItemDTO toDTO(NewsItem item) {
        return NewsItemDTO.builder()
                .id(item.getId())
                .title(item.getTitle())
                .link(item.getLink())
                .author(item.getAuthor())
                .publishedAt(item.getPublishedAt())
                .content(item.getCleanContent() != null ? item.getCleanContent() : item.getRawContent())
                .summary(item.getSummary())
                .tags(item.getTags())
                .sourceId(item.getSource() != null ? item.getSource().getId() : null)
                .sourceName(item.getSource() != null ? item.getSource().getName() : null)
                .readAt(item.getReadAt())
                .saved(item.getSaved())
                .score(item.getScore())
                .scoreReasoning(item.getScoreReasoning())
                .createdAt(item.getCreatedAt())
                .build();
    }

    /**
     * Exception thrown when search fails.
     */
    public static class SearchException extends RuntimeException {
        public SearchException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
