package be.transcode.morningdeck.server.config;

import be.transcode.morningdeck.server.core.model.SourceStatus;
import be.transcode.morningdeck.server.core.queue.FetchQueue;
import be.transcode.morningdeck.server.core.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class FeedIngestionHealthIndicator implements HealthIndicator {

    private final SourceRepository sourceRepository;

    @Autowired(required = false)
    private FetchQueue fetchQueue;

    @Value("${application.jobs.feed-ingestion.queue-capacity:1000}")
    private int queueCapacity;

    @Value("${application.jobs.feed-ingestion.stuck-threshold-minutes:10}")
    private int stuckThresholdMinutes;

    private static final double ERROR_THRESHOLD = 0.5; // 50% error rate is critical
    private static final double QUEUE_FULL_THRESHOLD = 0.9; // 90% queue capacity is concerning

    @Override
    public Health health() {
        long errorSources = sourceRepository.countByStatus(SourceStatus.ERROR);
        long totalSources = sourceRepository.count();

        Health.Builder builder;

        if (totalSources == 0) {
            builder = Health.up()
                    .withDetail("status", "No sources configured")
                    .withDetail("error_sources", 0)
                    .withDetail("total_sources", 0);
        } else {
            double errorRate = (double) errorSources / totalSources;

            if (errorRate > ERROR_THRESHOLD) {
                builder = Health.down()
                        .withDetail("error_rate", String.format("%.1f%%", errorRate * 100))
                        .withDetail("error_sources", errorSources)
                        .withDetail("total_sources", totalSources)
                        .withDetail("threshold", String.format("%.0f%%", ERROR_THRESHOLD * 100));
            } else {
                builder = Health.up()
                        .withDetail("error_rate", String.format("%.1f%%", errorRate * 100))
                        .withDetail("error_sources", errorSources)
                        .withDetail("total_sources", totalSources);
            }
        }

        // Add queue health if available
        if (fetchQueue != null) {
            int queueSize = fetchQueue.size();
            double queueUsage = (double) queueSize / queueCapacity;

            builder.withDetail("queue_size", queueSize)
                    .withDetail("queue_capacity", queueCapacity)
                    .withDetail("queue_usage", String.format("%.1f%%", queueUsage * 100));

            if (queueUsage > QUEUE_FULL_THRESHOLD) {
                builder = Health.down()
                        .withDetail("queue_warning", "Queue nearly full");
            }

            // Check for stuck sources
            Instant stuckThreshold = Instant.now().minusSeconds(stuckThresholdMinutes * 60L);
            long stuckSources = sourceRepository.countStuckSources(stuckThreshold);
            if (stuckSources > 0) {
                builder.withDetail("stuck_sources", stuckSources);
                if (stuckSources > 10) {
                    builder = Health.down()
                            .withDetail("stuck_warning", "Too many stuck sources");
                }
            }
        }

        return builder.build();
    }
}
