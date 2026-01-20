package be.transcode.morningdeck.server.core.search;

import be.transcode.morningdeck.server.core.model.NewsItem;
import be.transcode.morningdeck.server.core.model.NewsItemTags;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * Document model for Meilisearch indexing.
 * Maps NewsItem entity to a flat structure optimized for search.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsItemSearchDocument {

    // Primary key
    private String id;

    // Searchable text fields
    private String title;
    private String summary;
    private String content;
    private String author;

    // Tags - flattened for search
    @JsonProperty("tags_topics")
    private List<String> tagsTopics;

    @JsonProperty("tags_people")
    private List<String> tagsPeople;

    @JsonProperty("tags_companies")
    private List<String> tagsCompanies;

    @JsonProperty("tags_technologies")
    private List<String> tagsTechnologies;

    // Filterable metadata (CRITICAL for security)
    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("brief_id")
    private String briefId;

    @JsonProperty("source_id")
    private String sourceId;

    @JsonProperty("source_name")
    private String sourceName;

    // Filter fields
    @JsonProperty("is_read")
    private boolean isRead;

    private boolean saved;

    private Integer score;

    private String sentiment;

    // Timestamps as epoch seconds for Meilisearch filtering/sorting
    @JsonProperty("published_at")
    private Long publishedAt;

    @JsonProperty("created_at")
    private Long createdAt;

    /**
     * Create a search document from a NewsItem entity.
     * Requires the source relationship to be loaded for user/brief context.
     */
    public static NewsItemSearchDocument from(NewsItem item) {
        if (item == null) {
            return null;
        }

        var source = item.getSource();
        if (source == null) {
            throw new IllegalArgumentException("NewsItem must have source loaded for indexing");
        }

        var dayBrief = source.getDayBrief();
        if (dayBrief == null) {
            throw new IllegalArgumentException("Source must have dayBrief loaded for indexing");
        }

        NewsItemTags tags = item.getTags();

        return NewsItemSearchDocument.builder()
                .id(item.getId().toString())
                // Searchable fields
                .title(item.getTitle())
                .summary(item.getSummary())
                .content(getSearchableContent(item))
                .author(item.getAuthor())
                // Tags
                .tagsTopics(tags != null ? tags.getTopics() : Collections.emptyList())
                .tagsPeople(tags != null ? tags.getPeople() : Collections.emptyList())
                .tagsCompanies(tags != null ? tags.getCompanies() : Collections.emptyList())
                .tagsTechnologies(tags != null ? tags.getTechnologies() : Collections.emptyList())
                .sentiment(tags != null ? tags.getSentiment() : null)
                // Security metadata
                .userId(dayBrief.getUserId().toString())
                .briefId(dayBrief.getId().toString())
                .sourceId(source.getId().toString())
                .sourceName(source.getName())
                // Filter fields
                .isRead(item.getReadAt() != null)
                .saved(Boolean.TRUE.equals(item.getSaved()))
                .score(item.getScore())
                // Timestamps
                .publishedAt(item.getPublishedAt() != null ? item.getPublishedAt().getEpochSecond() : null)
                .createdAt(item.getCreatedAt() != null ? item.getCreatedAt().getEpochSecond() : null)
                .build();
    }

    /**
     * Get the best available content for search indexing.
     * Prefers clean content, falls back to summary or raw content.
     */
    private static String getSearchableContent(NewsItem item) {
        if (item.getCleanContent() != null && !item.getCleanContent().isBlank()) {
            return truncateForIndex(item.getCleanContent());
        }
        if (item.getWebContent() != null && !item.getWebContent().isBlank()) {
            return truncateForIndex(item.getWebContent());
        }
        if (item.getRawContent() != null && !item.getRawContent().isBlank()) {
            return truncateForIndex(item.getRawContent());
        }
        return null;
    }

    /**
     * Truncate content to avoid overly large documents.
     * Meilisearch handles large text but we want to keep index size reasonable.
     * Note: Content is guaranteed non-null when called from getSearchableContent.
     */
    private static String truncateForIndex(String content) {
        int maxLength = 10_000;
        return content.length() <= maxLength ? content : content.substring(0, maxLength);
    }
}
