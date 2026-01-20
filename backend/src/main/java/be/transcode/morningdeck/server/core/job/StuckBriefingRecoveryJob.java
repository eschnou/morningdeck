package be.transcode.morningdeck.server.core.job;

import be.transcode.morningdeck.server.core.repository.DayBriefRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Recovery job for briefings stuck in QUEUED or PROCESSING state.
 * This can happen after crashes or when workers fail unexpectedly.
 * Runs every 5 minutes and marks stuck briefings as ERROR (no retry to avoid AI token waste).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "application.jobs.briefing-execution", name = "enabled", havingValue = "true")
public class StuckBriefingRecoveryJob {

    private final DayBriefRepository dayBriefRepository;

    @Value("${application.jobs.briefing-execution.stuck-threshold-minutes:15}")
    private int stuckThresholdMinutes;

    /**
     * Periodic job to handle briefings stuck in transitional states.
     * Briefings are considered stuck if they've been in QUEUED/PROCESSING for longer
     * than the configured threshold (default: 15 minutes).
     * Stuck briefings are marked as ERROR - no retry to avoid wasting AI tokens.
     */
    @Scheduled(fixedRateString = "${application.jobs.briefing-execution.recovery-interval:300000}")
    @Transactional
    public void recoverStuckBriefings() {
        Instant stuckThreshold = Instant.now().minusSeconds(stuckThresholdMinutes * 60L);

        String errorMessage = "Briefing stuck in processing for more than " + stuckThresholdMinutes + " minutes";
        int errorCount = dayBriefRepository.markStuckBriefingsAsError(stuckThreshold, errorMessage);

        if (errorCount > 0) {
            log.warn("Marked {} stuck briefings as ERROR (threshold={}min)", errorCount, stuckThresholdMinutes);
        }
    }
}
