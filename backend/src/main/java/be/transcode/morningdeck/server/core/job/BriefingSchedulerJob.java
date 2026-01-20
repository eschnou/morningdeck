package be.transcode.morningdeck.server.core.job;

import be.transcode.morningdeck.server.core.model.BriefingFrequency;
import be.transcode.morningdeck.server.core.model.DayBrief;
import be.transcode.morningdeck.server.core.model.DayBriefStatus;
import be.transcode.morningdeck.server.core.queue.BriefingQueue;
import be.transcode.morningdeck.server.core.repository.DayBriefRepository;
import be.transcode.morningdeck.server.core.service.DayBriefService;
import be.transcode.morningdeck.server.core.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Scheduler job that finds briefings due for execution and enqueues them.
 * Runs every minute (configurable) and queues briefings in ACTIVE status.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "application.jobs.briefing-execution", name = "enabled", havingValue = "true")
public class BriefingSchedulerJob {

    private final DayBriefRepository dayBriefRepository;
    private final DayBriefService dayBriefService;
    private final BriefingQueue briefingQueue;
    private final SubscriptionService subscriptionService;

    /**
     * Scheduled job that runs every minute to find and enqueue briefings for execution.
     * Only ACTIVE briefings that are due (schedule time passed in their timezone and not executed today) are eligible.
     */
    @Scheduled(fixedRateString = "${application.jobs.briefing-execution.interval:60000}")
    public void scheduleBriefings() {
        if (!briefingQueue.canAccept()) {
            log.warn("Queue full, skipping scheduling cycle. queue_size={}", briefingQueue.size());
            return;
        }

        Instant nowUtc = Instant.now();
        Instant todayStartUtc = nowUtc.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();

        // Find ACTIVE briefings not executed today, then filter by timezone-aware schedule time
        List<DayBrief> candidateBriefings = dayBriefRepository.findActiveBriefingsNotExecutedToday(todayStartUtc);

        // Get users with available credits for efficient filtering
        Set<UUID> usersWithCredits = subscriptionService.getUserIdsWithCredits();

        List<DayBrief> dueBriefings = candidateBriefings.stream()
                .filter(briefing -> isBriefingDue(briefing, nowUtc))
                .filter(briefing -> usersWithCredits.contains(briefing.getUserId()))
                .toList();

        // Log briefings skipped due to credits
        long skippedDueToCredits = candidateBriefings.stream()
                .filter(briefing -> isBriefingDue(briefing, nowUtc))
                .filter(briefing -> !usersWithCredits.contains(briefing.getUserId()))
                .count();
        if (skippedDueToCredits > 0) {
            log.info("Skipped {} briefings due to insufficient credits", skippedDueToCredits);
        }

        if (dueBriefings.isEmpty()) {
            log.debug("No briefings due for scheduling");
            return;
        }

        log.info("Found {} briefings for scheduling", dueBriefings.size());

        int enqueuedCount = 0;
        for (DayBrief briefing : dueBriefings) {
            if (!briefingQueue.canAccept()) {
                log.warn("Queue full during scheduling, stopping. enqueued={}", enqueuedCount);
                break;
            }

            // Mark as queued in separate transaction (commits immediately)
            dayBriefService.updateStatus(briefing.getId(), DayBriefStatus.QUEUED);

            boolean success = briefingQueue.enqueue(briefing.getId());
            if (success) {
                enqueuedCount++;
            } else {
                // Rollback status if enqueue failed
                dayBriefService.updateStatus(briefing.getId(), DayBriefStatus.ACTIVE);
                log.warn("Failed to enqueue day_brief_id={}", briefing.getId());
            }
        }

        log.info("Scheduled {} briefings for execution", enqueuedCount);
    }

    /**
     * Check if a briefing is due for execution based on its configured timezone.
     * Converts the current UTC time to the briefing's timezone and compares with schedule time.
     * For WEEKLY briefs, also checks if today matches the configured day of week.
     */
    boolean isBriefingDue(DayBrief briefing, Instant nowUtc) {
        try {
            ZoneId userZone = ZoneId.of(briefing.getTimezone());
            ZonedDateTime userNow = nowUtc.atZone(userZone);
            LocalTime userCurrentTime = userNow.toLocalTime();

            // Check day-of-week for WEEKLY briefs
            if (briefing.getFrequency() == BriefingFrequency.WEEKLY) {
                DayOfWeek currentDay = userNow.getDayOfWeek();
                if (briefing.getScheduleDayOfWeek() != null
                        && briefing.getScheduleDayOfWeek() != currentDay) {
                    return false;
                }
            }

            return !briefing.getScheduleTime().isAfter(userCurrentTime);
        } catch (Exception e) {
            log.warn("Invalid timezone for briefing day_brief_id={}, timezone={}, defaulting to UTC",
                    briefing.getId(), briefing.getTimezone());
            // Fall back to UTC comparison if timezone is invalid
            LocalTime utcCurrentTime = nowUtc.atZone(ZoneOffset.UTC).toLocalTime();
            return !briefing.getScheduleTime().isAfter(utcCurrentTime);
        }
    }
}
