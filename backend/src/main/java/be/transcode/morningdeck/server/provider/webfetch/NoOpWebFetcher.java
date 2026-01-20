package be.transcode.morningdeck.server.provider.webfetch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * No-operation implementation of WebContentFetcher.
 * Used when web fetching is disabled via configuration.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "application.web-fetch", name = "enabled", havingValue = "false")
public class NoOpWebFetcher implements WebContentFetcher {

    public NoOpWebFetcher() {
        log.info("Web content fetching is disabled");
    }

    @Override
    public Optional<String> fetch(String url) {
        return Optional.empty();
    }
}
