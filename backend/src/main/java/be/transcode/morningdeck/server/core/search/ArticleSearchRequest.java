package be.transcode.morningdeck.server.core.search;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * Request object for article search via Meilisearch.
 */
@Data
@Builder
public class ArticleSearchRequest {

    /**
     * The search query string.
     */
    private String query;

    /**
     * User ID for security filtering (REQUIRED).
     */
    private UUID userId;

    /**
     * Brief ID to scope the search (REQUIRED).
     */
    private UUID briefId;

    /**
     * Optional: Filter by specific source.
     */
    private UUID sourceId;

    /**
     * Optional: Filter by read status ("READ" or "UNREAD").
     */
    private String readStatus;

    /**
     * Optional: Filter by saved status.
     */
    private Boolean saved;

    /**
     * Optional: Filter by minimum score.
     */
    private Integer minScore;

    /**
     * Page number (0-indexed).
     */
    @Builder.Default
    private int page = 0;

    /**
     * Page size.
     */
    @Builder.Default
    private int size = 20;
}
