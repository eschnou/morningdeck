package be.transcode.morningdeck.server.provider.webfetch;

import java.util.Optional;

/**
 * Interface for fetching and extracting article content from web URLs.
 * Implementations handle HTTP fetching, content extraction, and conversion to markdown.
 */
public interface WebContentFetcher {

    /**
     * Fetches and extracts article content from a URL.
     *
     * @param url The article URL to fetch
     * @return Extracted content as markdown, or empty Optional if fetch fails or is skipped
     */
    Optional<String> fetch(String url);
}
