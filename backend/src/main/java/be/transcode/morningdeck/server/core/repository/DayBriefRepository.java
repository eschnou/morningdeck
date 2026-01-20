package be.transcode.morningdeck.server.core.repository;

import be.transcode.morningdeck.server.core.model.DayBrief;
import be.transcode.morningdeck.server.core.model.DayBriefStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public interface DayBriefRepository extends JpaRepository<DayBrief, UUID> {

    Page<DayBrief> findByUserIdAndStatus(UUID userId, DayBriefStatus status, Pageable pageable);

    Page<DayBrief> findByUserId(UUID userId, Pageable pageable);

    // Ordered queries for position-based ordering
    Page<DayBrief> findByUserIdOrderByPositionAsc(UUID userId, Pageable pageable);

    Page<DayBrief> findByUserIdAndStatusOrderByPositionAsc(UUID userId, DayBriefStatus status, Pageable pageable);

    // For reorder operation
    List<DayBrief> findByUserId(UUID userId);

    // Get max position for new briefs
    @Query("SELECT COALESCE(MAX(d.position), -1) FROM DayBrief d WHERE d.userId = :userId")
    Integer findMaxPositionByUserId(@Param("userId") UUID userId);

    /**
     * Find DayBrief with sources eagerly loaded.
     * Used by BriefingWorker to ensure sources are available for report generation.
     */
    @Query("SELECT d FROM DayBrief d LEFT JOIN FETCH d.sources WHERE d.id = :id")
    java.util.Optional<DayBrief> findByIdWithSources(@Param("id") UUID id);

    @Query("SELECT d FROM DayBrief d WHERE d.status = :status AND d.scheduleTime <= :currentTime " +
           "AND (d.lastExecutedAt IS NULL OR d.lastExecutedAt < :todayStart)")
    List<DayBrief> findDueDailyBriefings(
            @Param("status") DayBriefStatus status,
            @Param("currentTime") LocalTime currentTime,
            @Param("todayStart") Instant todayStart);

    /**
     * Find briefings that are ACTIVE and due for scheduled execution.
     * Used by BriefingSchedulerJob to find briefings to enqueue.
     */
    @Query("SELECT d FROM DayBrief d WHERE d.status = 'ACTIVE' " +
           "AND d.scheduleTime <= :currentTime " +
           "AND (d.lastExecutedAt IS NULL OR d.lastExecutedAt < :todayStart)")
    List<DayBrief> findDueBriefingsForScheduling(
            @Param("currentTime") LocalTime currentTime,
            @Param("todayStart") Instant todayStart);

    /**
     * Find ACTIVE briefings that have not been executed today.
     * Used by BriefingSchedulerJob which performs timezone-aware filtering in Java.
     */
    @Query("SELECT d FROM DayBrief d WHERE d.status = 'ACTIVE' " +
           "AND (d.lastExecutedAt IS NULL OR d.lastExecutedAt < :todayStartUtc)")
    List<DayBrief> findActiveBriefingsNotExecutedToday(
            @Param("todayStartUtc") Instant todayStartUtc);

    /**
     * Mark stuck briefings (in QUEUED or PROCESSING state for too long) as ERROR.
     * Used by StuckBriefingRecoveryJob. No retry - stuck briefings go to ERROR state.
     */
    @Modifying
    @Query("UPDATE DayBrief d SET d.status = 'ERROR', d.errorMessage = :errorMessage " +
           "WHERE (d.status = 'QUEUED' AND d.queuedAt < :threshold) " +
           "OR (d.status = 'PROCESSING' AND d.processingStartedAt < :threshold)")
    int markStuckBriefingsAsError(@Param("threshold") Instant threshold, @Param("errorMessage") String errorMessage);
}
