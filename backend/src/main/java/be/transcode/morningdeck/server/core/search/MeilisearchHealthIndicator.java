package be.transcode.morningdeck.server.core.search;

import com.meilisearch.sdk.Client;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Health indicator for Meilisearch service.
 * Reports UP when Meilisearch is reachable, DOWN otherwise.
 */
@Slf4j
@Component("meilisearch")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "meilisearch.enabled", havingValue = "true")
public class MeilisearchHealthIndicator implements HealthIndicator {

    private final Client meilisearchClient;
    private final MeilisearchIndexService indexService;

    @Override
    public Health health() {
        try {
            // Check if Meilisearch is healthy
            meilisearchClient.health();

            // Get index stats for additional details
            var stats = indexService.getStats();

            return Health.up()
                    .withDetail("status", "operational")
                    .withDetail("documentCount", stats.documentCount())
                    .withDetail("isIndexing", stats.isIndexing())
                    .build();

        } catch (Exception e) {
            log.warn("Meilisearch health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
