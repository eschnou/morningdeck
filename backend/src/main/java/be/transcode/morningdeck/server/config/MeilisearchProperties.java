package be.transcode.morningdeck.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "meilisearch")
public class MeilisearchProperties {

    /**
     * Enable/disable Meilisearch integration.
     * When disabled, the application works without search functionality.
     */
    private boolean enabled = false;

    /**
     * Meilisearch server URL (e.g., http://localhost:7700)
     */
    private String host = "http://localhost:7700";

    /**
     * Meilisearch API key (master key or search key)
     */
    private String apiKey;

    /**
     * Index name for news items
     */
    private String indexName = "news_items";
}
