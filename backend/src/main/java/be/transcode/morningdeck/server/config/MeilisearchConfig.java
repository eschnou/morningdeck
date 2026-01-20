package be.transcode.morningdeck.server.config;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import com.meilisearch.sdk.Index;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "meilisearch.enabled", havingValue = "true")
public class MeilisearchConfig {

    private final MeilisearchProperties properties;

    @Bean
    public Client meilisearchClient() {
        log.info("Initializing Meilisearch client: host={}", properties.getHost());
        Config config = new Config(properties.getHost(), properties.getApiKey());
        return new Client(config);
    }

    @Bean
    public Index newsItemsIndex(Client client) {
        log.info("Getting or creating Meilisearch index: {}", properties.getIndexName());
        return client.index(properties.getIndexName());
    }
}
