package be.transcode.morningdeck.server.config;

import be.transcode.morningdeck.server.core.model.NewsItemStatus;
import be.transcode.morningdeck.server.core.queue.ProcessingQueue;
import be.transcode.morningdeck.server.core.repository.NewsItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class NewsProcessingHealthIndicator implements HealthIndicator {

    private final NewsItemRepository newsItemRepository;

    @Autowired(required = false)
    private ProcessingQueue processingQueue;

    @Value("${application.jobs.news-processing.queue-capacity:500}")
    private int queueCapacity;

    @Value("${application.jobs.news-processing.stuck-threshold-minutes:10}")
    private int stuckThresholdMinutes;

    private static final double ERROR_THRESHOLD = 0.3; // 30% error rate is critical
    private static final double QUEUE_FULL_THRESHOLD = 0.9; // 90% queue capacity is concerning

    @Override
    public Health health() {
        long newItems = newsItemRepository.countByStatus(NewsItemStatus.NEW);
        long pendingItems = newsItemRepository.countByStatus(NewsItemStatus.PENDING);
        long processingItems = newsItemRepository.countByStatus(NewsItemStatus.PROCESSING);
        long doneItems = newsItemRepository.countByStatus(NewsItemStatus.DONE);
        long errorItems = newsItemRepository.countByStatus(NewsItemStatus.ERROR);
        long totalItems = newItems + pendingItems + processingItems + doneItems + errorItems;

        Health.Builder builder;

        if (totalItems == 0) {
            builder = Health.up()
                    .withDetail("status", "No news items")
                    .withDetail("new", 0)
                    .withDetail("error", 0);
        } else {
            double errorRate = (double) errorItems / totalItems;

            if (errorRate > ERROR_THRESHOLD) {
                builder = Health.down()
                        .withDetail("error_rate", String.format("%.1f%%", errorRate * 100))
                        .withDetail("error_items", errorItems)
                        .withDetail("total_items", totalItems)
                        .withDetail("threshold", String.format("%.0f%%", ERROR_THRESHOLD * 100));
            } else {
                builder = Health.up()
                        .withDetail("new", newItems)
                        .withDetail("pending", pendingItems)
                        .withDetail("processing", processingItems)
                        .withDetail("done", doneItems)
                        .withDetail("error", errorItems);
            }
        }

        // Add queue health if available
        if (processingQueue != null) {
            int queueSize = processingQueue.size();
            double queueUsage = (double) queueSize / queueCapacity;

            builder.withDetail("queue_size", queueSize)
                    .withDetail("queue_capacity", queueCapacity)
                    .withDetail("queue_usage", String.format("%.1f%%", queueUsage * 100));

            if (queueUsage > QUEUE_FULL_THRESHOLD) {
                builder = Health.down()
                        .withDetail("queue_warning", "Queue nearly full");
            }

            // Check for stuck items
            Instant stuckThreshold = Instant.now().minusSeconds(stuckThresholdMinutes * 60L);
            long stuckItems = newsItemRepository.countStuckItems(stuckThreshold);
            if (stuckItems > 0) {
                builder.withDetail("stuck_items", stuckItems);
                if (stuckItems > 20) {
                    builder = Health.down()
                            .withDetail("stuck_warning", "Too many stuck items");
                }
            }
        }

        return builder.build();
    }
}
