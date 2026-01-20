package be.transcode.morningdeck.server.provider.sourcefetch;

import be.transcode.morningdeck.server.core.model.Source;
import be.transcode.morningdeck.server.core.model.SourceType;
import be.transcode.morningdeck.server.provider.sourcefetch.model.FetchedItem;
import be.transcode.morningdeck.server.provider.sourcefetch.model.SourceValidationResult;

import java.time.Instant;
import java.util.List;

public interface SourceFetcher {

    /**
     * Returns the source type this fetcher handles
     */
    SourceType getSourceType();

    /**
     * Validates if the URL is a valid source for this type
     */
    SourceValidationResult validate(String url);

    /**
     * Fetches items from the source
     *
     * @param source        The source entity
     * @param lastFetchedAt Only fetch items newer than this (null for first fetch)
     * @return List of raw feed items
     */
    List<FetchedItem> fetch(Source source, Instant lastFetchedAt);
}
