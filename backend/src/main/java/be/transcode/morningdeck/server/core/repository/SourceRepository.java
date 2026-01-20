package be.transcode.morningdeck.server.core.repository;

import be.transcode.morningdeck.server.core.model.FetchStatus;
import be.transcode.morningdeck.server.core.model.Source;
import be.transcode.morningdeck.server.core.model.SourceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SourceRepository extends JpaRepository<Source, UUID> {

    // Find sources by briefing
    Page<Source> findByDayBriefId(UUID dayBriefId, Pageable pageable);

    Page<Source> findByDayBriefIdAndStatus(UUID dayBriefId, SourceStatus status, Pageable pageable);

    List<Source> findByDayBriefId(UUID dayBriefId);

    // Find sources by user (via dayBrief)
    @Query("SELECT s FROM Source s WHERE s.dayBrief.userId = :userId")
    Page<Source> findByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT s FROM Source s WHERE s.dayBrief.userId = :userId AND s.status = :status")
    Page<Source> findByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") SourceStatus status, Pageable pageable);

    // Check for duplicate URL within briefing
    boolean existsByDayBriefIdAndUrl(UUID dayBriefId, String url);

    // Check for duplicate URL within user (across all briefings)
    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM Source s WHERE s.dayBrief.userId = :userId AND s.url = :url")
    boolean existsByUserIdAndUrl(@Param("userId") UUID userId, @Param("url") String url);

    List<Source> findByStatus(SourceStatus status);

    long countByStatus(SourceStatus status);

    @Query("SELECT s.id FROM Source s WHERE s.dayBrief.userId = :userId")
    List<UUID> findSourceIdsByUserId(@Param("userId") UUID userId);

    @Query("SELECT s.id FROM Source s WHERE s.dayBrief.id = :briefingId")
    List<UUID> findSourceIdsByBriefingId(@Param("briefingId") UUID briefingId);

    // Feed queue scheduling queries
    @Query("""
        SELECT s FROM Source s
        WHERE s.status = be.transcode.morningdeck.server.core.model.SourceStatus.ACTIVE
          AND s.fetchStatus = be.transcode.morningdeck.server.core.model.FetchStatus.IDLE
          AND (s.lastFetchedAt IS NULL OR s.lastFetchedAt < :cutoff)
        ORDER BY s.lastFetchedAt ASC NULLS FIRST
        """)
    List<Source> findSourcesDueForRefreshSince(@Param("cutoff") Instant cutoff, Pageable pageable);

    default List<Source> findSourcesDueForRefresh(int limit) {
        Instant cutoff = Instant.now().minusSeconds(15 * 60);
        return findSourcesDueForRefreshSince(cutoff, org.springframework.data.domain.PageRequest.of(0, limit));
    }

    // Stuck source recovery queries
    @Modifying
    @Query("""
        UPDATE Source s
        SET s.fetchStatus = be.transcode.morningdeck.server.core.model.FetchStatus.IDLE,
            s.queuedAt = NULL
        WHERE s.fetchStatus = be.transcode.morningdeck.server.core.model.FetchStatus.QUEUED
          AND s.queuedAt < :threshold
        """)
    int resetStuckQueuedSources(@Param("threshold") Instant threshold);

    @Modifying
    @Query("""
        UPDATE Source s
        SET s.fetchStatus = be.transcode.morningdeck.server.core.model.FetchStatus.IDLE,
            s.fetchStartedAt = NULL
        WHERE s.fetchStatus = be.transcode.morningdeck.server.core.model.FetchStatus.FETCHING
          AND s.fetchStartedAt < :threshold
        """)
    int resetStuckFetchingSources(@Param("threshold") Instant threshold);

    @Query("""
        SELECT COUNT(s) FROM Source s
        WHERE (s.fetchStatus = be.transcode.morningdeck.server.core.model.FetchStatus.QUEUED AND s.queuedAt < :threshold)
           OR (s.fetchStatus = be.transcode.morningdeck.server.core.model.FetchStatus.FETCHING AND s.fetchStartedAt < :threshold)
        """)
    long countStuckSources(@Param("threshold") Instant threshold);

    long countByFetchStatus(FetchStatus fetchStatus);

    Optional<Source> findByEmailAddress(UUID emailAddress);
}
