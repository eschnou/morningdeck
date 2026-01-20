package be.transcode.morningdeck.server.provider.sourcefetch;

import be.transcode.morningdeck.server.core.model.Source;
import be.transcode.morningdeck.server.core.model.SourceType;
import be.transcode.morningdeck.server.provider.sourcefetch.model.FetchedItem;
import be.transcode.morningdeck.server.provider.sourcefetch.model.SourceValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * No-op fetcher for EMAIL type sources.
 * Email sources receive content via push (EmailReceivedEvent), not periodic polling.
 */
@Slf4j
@Component
public class EmailSourceFetcher implements SourceFetcher {

    @Override
    public SourceType getSourceType() {
        return SourceType.EMAIL;
    }

    @Override
    public SourceValidationResult validate(String url) {
        // EMAIL sources always validate successfully - they don't have a URL to validate
        return SourceValidationResult.success(
                "Email Source",
                "Receives emails at generated address"
        );
    }

    @Override
    public List<FetchedItem> fetch(Source source, Instant lastFetchedAt) {
        // No periodic fetch for email sources - emails arrive via events
        log.debug("EmailSourceFetcher.fetch called for source {} - returning empty list (emails arrive via events)", source.getId());
        return List.of();
    }
}
