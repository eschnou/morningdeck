package be.transcode.morningdeck.server.core.job;

import be.transcode.morningdeck.server.core.repository.NewsItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Recovery job for news items stuck in PENDING or PROCESSING state.
 * This can happen after crashes or when workers fail unexpectedly.
 * Runs every 5 minutes and marks stuck items as ERROR.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "application.jobs.news-processing", name = "enabled", havingValue = "true")
public class StuckNewsItemRecoveryJob {

    private final NewsItemRepository newsItemRepository;

    @Value("${application.jobs.news-processing.stuck-threshold-minutes:10}")
    private int stuckThresholdMinutes;

    /**
     * Periodic job to recover news items stuck in transitional states.
     * Items are considered stuck if they've been in PENDING/PROCESSING for longer
     * than the configured threshold (default: 10 minutes).
     * Stuck items are marked as ERROR to prevent infinite processing loops.
     */
    @Scheduled(fixedRateString = "${application.jobs.news-processing.recovery-interval:300000}")
    @Transactional
    public void recoverStuckItems() {
        Instant stuckThreshold = Instant.now().minusSeconds(stuckThresholdMinutes * 60L);

        // Mark stuck items as ERROR
        String errorMessage = "Item stuck in processing for more than " + stuckThresholdMinutes + " minutes";
        int errorCount = newsItemRepository.markStuckItemsAsError(stuckThreshold, errorMessage);

        if (errorCount > 0) {
            log.warn("Marked {} stuck news items as ERROR (threshold={}min)", errorCount, stuckThresholdMinutes);
        }
    }
}
