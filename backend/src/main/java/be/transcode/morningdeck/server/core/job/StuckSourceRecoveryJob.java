package be.transcode.morningdeck.server.core.job;

import be.transcode.morningdeck.server.core.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Recovery job for sources stuck in QUEUED or FETCHING state.
 * This can happen after crashes or when workers fail unexpectedly.
 * Runs every 5 minutes and resets sources that have been stuck too long.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "application.jobs.feed-ingestion", name = "enabled", havingValue = "true")
public class StuckSourceRecoveryJob {

    private final SourceRepository sourceRepository;

    @Value("${application.jobs.feed-ingestion.stuck-threshold-minutes:10}")
    private int stuckThresholdMinutes;

    /**
     * Periodic job to recover sources stuck in transitional states.
     * Sources are considered stuck if they've been in QUEUED/FETCHING for longer
     * than the configured threshold (default: 10 minutes).
     */
    @Scheduled(fixedRateString = "${application.jobs.feed-recovery.interval:300000}")
    @Transactional
    public void recoverStuckSources() {
        Instant stuckThreshold = Instant.now().minusSeconds(stuckThresholdMinutes * 60L);

        // Reset sources stuck in QUEUED state
        int queuedReset = sourceRepository.resetStuckQueuedSources(stuckThreshold);
        if (queuedReset > 0) {
            log.warn("Reset {} sources stuck in QUEUED state (threshold={}min)",
                    queuedReset, stuckThresholdMinutes);
        }

        // Reset sources stuck in FETCHING state
        int fetchingReset = sourceRepository.resetStuckFetchingSources(stuckThreshold);
        if (fetchingReset > 0) {
            log.warn("Reset {} sources stuck in FETCHING state (threshold={}min)",
                    fetchingReset, stuckThresholdMinutes);
        }

        if (queuedReset > 0 || fetchingReset > 0) {
            log.info("Stuck source recovery complete: queued_reset={}, fetching_reset={}",
                    queuedReset, fetchingReset);
        }
    }
}
