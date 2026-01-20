package be.transcode.morningdeck.server.core.repository;

import be.transcode.morningdeck.server.core.model.NewsItem;
import be.transcode.morningdeck.server.core.model.NewsItemStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NewsItemRepository extends JpaRepository<NewsItem, UUID> {

    boolean existsBySourceIdAndGuid(UUID sourceId, String guid);

    List<NewsItem> findByStatusInOrderByCreatedAtAsc(List<NewsItemStatus> statuses, Pageable pageable);

    @Query("SELECT n FROM NewsItem n WHERE n.source.id IN :sourceIds AND n.status = :status AND n.publishedAt > :after")
    List<NewsItem> findBySourceIdInAndStatusAndPublishedAtAfter(
            @Param("sourceIds") List<UUID> sourceIds,
            @Param("status") NewsItemStatus status,
            @Param("after") Instant after);

    /**
     * Find top scored items for report generation.
     * Items are pre-scored during processing, so this just retrieves and sorts them.
     */
    @Query("""
        SELECT n FROM NewsItem n
        WHERE n.source.id IN :sourceIds
          AND n.status = :status
          AND n.publishedAt > :after
          AND n.score IS NOT NULL
        ORDER BY n.score DESC, n.publishedAt DESC
        """)
    List<NewsItem> findTopScoredItems(
            @Param("sourceIds") List<UUID> sourceIds,
            @Param("status") NewsItemStatus status,
            @Param("after") Instant after,
            Pageable pageable);

    long countBySourceId(UUID sourceId);

    long countBySourceIdAndReadAtIsNull(UUID sourceId);

    Page<NewsItem> findBySourceId(UUID sourceId, Pageable pageable);

    @Query("SELECT n FROM NewsItem n WHERE n.source.dayBrief.userId = :userId")
    Page<NewsItem> findByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT n FROM NewsItem n WHERE n.source.dayBrief.userId = :userId AND " +
           "(LOWER(n.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(n.cleanContent) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<NewsItem> searchByUserIdAndQuery(
            @Param("userId") UUID userId,
            @Param("query") String query,
            Pageable pageable);

    @Query("SELECT n FROM NewsItem n WHERE n.source.dayBrief.userId = :userId AND n.source.id = :sourceId")
    Page<NewsItem> findByUserIdAndSourceId(
            @Param("userId") UUID userId,
            @Param("sourceId") UUID sourceId,
            Pageable pageable);

    @Query("SELECT n FROM NewsItem n WHERE n.source.dayBrief.userId = :userId AND " +
           "n.publishedAt >= :from AND n.publishedAt <= :to")
    Page<NewsItem> findByUserIdAndDateRange(
            @Param("userId") UUID userId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    @Query("SELECT n FROM NewsItem n WHERE n.source.id IN :sourceIds " +
           "AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(n.summary) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "AND (:from IS NULL OR n.publishedAt >= :from) " +
           "AND (:to IS NULL OR n.publishedAt <= :to) " +
           "ORDER BY n.publishedAt DESC")
    Page<NewsItem> searchByQuery(
            @Param("sourceIds") List<UUID> sourceIds,
            @Param("query") String query,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    @Query("SELECT n FROM NewsItem n WHERE n.source.id IN :sourceIds " +
           "AND n.publishedAt >= :from " +
           "AND n.publishedAt <= :to " +
           "ORDER BY n.publishedAt DESC")
    Page<NewsItem> findBySourceIdInAndDateRange(
            @Param("sourceIds") List<UUID> sourceIds,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    @Query("SELECT n FROM NewsItem n WHERE n.source.id IN :sourceIds ORDER BY n.publishedAt DESC")
    Page<NewsItem> findBySourceIdIn(
            @Param("sourceIds") List<UUID> sourceIds,
            Pageable pageable);

    @Query("SELECT n FROM NewsItem n WHERE n.source.id IN :sourceIds " +
           "AND (:readStatus IS NULL OR " +
           "(:readStatus = 'UNREAD' AND n.readAt IS NULL) OR " +
           "(:readStatus = 'READ' AND n.readAt IS NOT NULL)) " +
           "AND (:saved IS NULL OR n.saved = :saved) " +
           "AND (:minScore IS NULL OR n.score >= :minScore) " +
           "ORDER BY n.publishedAt DESC")
    Page<NewsItem> findBySourceIdsWithFilters(
            @Param("sourceIds") List<UUID> sourceIds,
            @Param("readStatus") String readStatus,
            @Param("saved") Boolean saved,
            @Param("minScore") Integer minScore,
            Pageable pageable);

    /**
     * Find news items for a brief using JOIN (more efficient than IN clause).
     */
    @Query("""
        SELECT n FROM NewsItem n
        JOIN n.source s
        WHERE s.dayBrief.id = :briefId
        ORDER BY n.publishedAt DESC
        """)
    Page<NewsItem> findByBriefIdOrderByPublishedAt(
            @Param("briefId") UUID briefId,
            Pageable pageable);

    /**
     * Find news items for a brief with filters using JOIN.
     * Optionally filter by specific source within the brief.
     */
    @Query("""
        SELECT n FROM NewsItem n
        JOIN n.source s
        WHERE s.dayBrief.id = :briefId
          AND (:sourceId IS NULL OR s.id = :sourceId)
          AND (:readStatus IS NULL OR
               (:readStatus = 'UNREAD' AND n.readAt IS NULL) OR
               (:readStatus = 'READ' AND n.readAt IS NOT NULL))
          AND (:saved IS NULL OR n.saved = :saved)
          AND (:minScore IS NULL OR n.score >= :minScore)
        ORDER BY n.publishedAt DESC
        """)
    Page<NewsItem> findByBriefIdWithFilters(
            @Param("briefId") UUID briefId,
            @Param("sourceId") UUID sourceId,
            @Param("readStatus") String readStatus,
            @Param("saved") Boolean saved,
            @Param("minScore") Integer minScore,
            Pageable pageable);

    @Query("SELECT n FROM NewsItem n WHERE n.source.id IN :sourceIds " +
           "AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(n.summary) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "ORDER BY n.publishedAt DESC")
    Page<NewsItem> searchByQueryWithoutDateFilter(
            @Param("sourceIds") List<UUID> sourceIds,
            @Param("query") String query,
            Pageable pageable);

    /**
     * Search items by query with all filters (read status, saved, min score).
     * Used as PostgreSQL fallback when Meilisearch is disabled.
     */
    @Query("SELECT n FROM NewsItem n WHERE n.source.id IN :sourceIds " +
           "AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(n.summary) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "AND (:readStatus IS NULL OR " +
           "(:readStatus = 'UNREAD' AND n.readAt IS NULL) OR " +
           "(:readStatus = 'READ' AND n.readAt IS NOT NULL)) " +
           "AND (:saved IS NULL OR n.saved = :saved) " +
           "AND (:minScore IS NULL OR n.score >= :minScore) " +
           "ORDER BY n.publishedAt DESC")
    Page<NewsItem> searchBySourceIdsWithFilters(
            @Param("sourceIds") List<UUID> sourceIds,
            @Param("query") String query,
            @Param("readStatus") String readStatus,
            @Param("saved") Boolean saved,
            @Param("minScore") Integer minScore,
            Pageable pageable);

    /**
     * Search news items for a brief with text query and filters using JOIN.
     */
    @Query("""
        SELECT n FROM NewsItem n
        JOIN n.source s
        WHERE s.dayBrief.id = :briefId
          AND (:sourceId IS NULL OR s.id = :sourceId)
          AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :query, '%')) OR
               LOWER(n.summary) LIKE LOWER(CONCAT('%', :query, '%')))
          AND (:readStatus IS NULL OR
               (:readStatus = 'UNREAD' AND n.readAt IS NULL) OR
               (:readStatus = 'READ' AND n.readAt IS NOT NULL))
          AND (:saved IS NULL OR n.saved = :saved)
          AND (:minScore IS NULL OR n.score >= :minScore)
        ORDER BY n.publishedAt DESC
        """)
    Page<NewsItem> searchByBriefIdWithFilters(
            @Param("briefId") UUID briefId,
            @Param("sourceId") UUID sourceId,
            @Param("query") String query,
            @Param("readStatus") String readStatus,
            @Param("saved") Boolean saved,
            @Param("minScore") Integer minScore,
            Pageable pageable);

    // Find items ready for processing (status = NEW)
    @Query("""
        SELECT n FROM NewsItem n
        WHERE n.status = be.transcode.morningdeck.server.core.model.NewsItemStatus.NEW
        ORDER BY n.createdAt ASC
        """)
    List<NewsItem> findItemsForProcessing(Pageable pageable);

    // Stuck item recovery: mark items stuck in PENDING or PROCESSING as ERROR
    @Modifying
    @Query("""
        UPDATE NewsItem n
        SET n.status = be.transcode.morningdeck.server.core.model.NewsItemStatus.ERROR,
            n.errorMessage = :errorMessage
        WHERE n.status IN (be.transcode.morningdeck.server.core.model.NewsItemStatus.PENDING,
                           be.transcode.morningdeck.server.core.model.NewsItemStatus.PROCESSING)
          AND n.updatedAt < :threshold
        """)
    int markStuckItemsAsError(@Param("threshold") Instant threshold, @Param("errorMessage") String errorMessage);

    @Query("""
        SELECT COUNT(n) FROM NewsItem n
        WHERE n.status IN (be.transcode.morningdeck.server.core.model.NewsItemStatus.PENDING,
                           be.transcode.morningdeck.server.core.model.NewsItemStatus.PROCESSING)
          AND n.updatedAt < :threshold
        """)
    long countStuckItems(@Param("threshold") Instant threshold);

    long countByStatus(NewsItemStatus status);

    /**
     * Mark all unread items for a source as read.
     * Returns the count of items updated.
     */
    @Modifying
    @Query("""
        UPDATE NewsItem n
        SET n.readAt = :readAt, n.updatedAt = :updatedAt
        WHERE n.source.id = :sourceId
          AND n.readAt IS NULL
        """)
    int markAllAsReadBySourceId(
            @Param("sourceId") UUID sourceId,
            @Param("readAt") Instant readAt,
            @Param("updatedAt") Instant updatedAt);

    /**
     * Mark all unread items for multiple sources as read.
     * Used for briefing-level bulk operations.
     * Returns the count of items updated.
     */
    @Modifying
    @Query("""
        UPDATE NewsItem n
        SET n.readAt = :readAt, n.updatedAt = :updatedAt
        WHERE n.source.id IN :sourceIds
          AND n.readAt IS NULL
        """)
    int markAllAsReadBySourceIds(
            @Param("sourceIds") List<UUID> sourceIds,
            @Param("readAt") Instant readAt,
            @Param("updatedAt") Instant updatedAt);

    /**
     * Find all items for a brief (for Meilisearch reindexing).
     */
    @Query("""
        SELECT n FROM NewsItem n
        JOIN FETCH n.source s
        JOIN FETCH s.dayBrief
        WHERE s.dayBrief.id = :briefId
        ORDER BY n.publishedAt DESC
        """)
    Page<NewsItem> findByBriefId(@Param("briefId") UUID briefId, Pageable pageable);

    /**
     * Find all items with source and brief eagerly loaded (for Meilisearch full reindexing).
     */
    @Query("""
        SELECT n FROM NewsItem n
        JOIN FETCH n.source s
        JOIN FETCH s.dayBrief
        ORDER BY n.id
        """)
    Page<NewsItem> findAllWithSourceAndBrief(Pageable pageable);

    /**
     * Find items by IDs with source eagerly loaded (for Meilisearch search results).
     */
    @Query("""
        SELECT n FROM NewsItem n
        JOIN FETCH n.source
        WHERE n.id IN :ids
        """)
    List<NewsItem> findAllByIdWithSource(@Param("ids") List<UUID> ids);
}
