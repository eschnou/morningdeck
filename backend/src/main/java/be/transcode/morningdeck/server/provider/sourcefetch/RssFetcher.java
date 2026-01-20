package be.transcode.morningdeck.server.provider.sourcefetch;

import be.transcode.morningdeck.server.core.exception.SourceFetchException;
import be.transcode.morningdeck.server.core.model.Source;
import be.transcode.morningdeck.server.core.model.SourceType;
import be.transcode.morningdeck.server.core.util.HtmlToMarkdownConverter;
import be.transcode.morningdeck.server.provider.sourcefetch.model.FetchedItem;
import be.transcode.morningdeck.server.provider.sourcefetch.model.SourceValidationResult;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RssFetcher implements SourceFetcher {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private final HtmlToMarkdownConverter htmlToMarkdownConverter;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public SourceType getSourceType() {
        return SourceType.RSS;
    }

    @Override
    public SourceValidationResult validate(String url) {
        try {
            SyndFeed feed = fetchFeed(url, null, null);
            return SourceValidationResult.success(
                    feed.getTitle(),
                    feed.getDescription()
            );
        } catch (Exception e) {
            log.warn("Failed to validate RSS feed at {}: {}", url, e.getMessage());
            return SourceValidationResult.failure("Invalid RSS feed: " + e.getMessage());
        }
    }

    @Override
    public List<FetchedItem> fetch(Source source, Instant lastFetchedAt) {
        try {
            SyndFeed feed = fetchFeed(source.getUrl(), source.getEtag(), source.getLastModified());
            List<FetchedItem> items = new ArrayList<>();

            for (SyndEntry entry : feed.getEntries()) {
                Instant publishedAt = extractPublishedDate(entry);

                // Skip items older than lastFetchedAt if provided
                if (lastFetchedAt != null && publishedAt != null && publishedAt.isBefore(lastFetchedAt)) {
                    continue;
                }

                String rawContent = extractContent(entry);
                String cleanContent = htmlToMarkdownConverter.convert(rawContent);

                FetchedItem item = FetchedItem.builder()
                        .guid(extractGuid(entry))
                        .title(entry.getTitle())
                        .link(entry.getLink())
                        .author(entry.getAuthor())
                        .publishedAt(publishedAt)
                        .rawContent(rawContent)
                        .cleanContent(cleanContent)
                        .build();

                items.add(item);
            }

            log.info("Fetched {} items from source {}", items.size(), source.getId());
            return items;
        } catch (Exception e) {
            throw new SourceFetchException("Failed to fetch RSS feed: " + e.getMessage(), e);
        }
    }

    private SyndFeed fetchFeed(String url, String etag, String lastModified) throws IOException, FeedException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .GET();

        // Add caching headers if available
        if (etag != null) {
            requestBuilder.header("If-None-Match", etag);
        }
        if (lastModified != null) {
            requestBuilder.header("If-Modified-Since", lastModified);
        }

        try {
            HttpRequest request = requestBuilder.build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 304) {
                // Not modified - return empty feed
                throw new SourceFetchException("Feed not modified since last fetch");
            }

            if (response.statusCode() != 200) {
                throw new SourceFetchException("HTTP error: " + response.statusCode());
            }

            SyndFeedInput input = new SyndFeedInput();
            try (XmlReader reader = new XmlReader(new java.io.ByteArrayInputStream(response.body()))) {
                return input.build(reader);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SourceFetchException("Request interrupted", e);
        }
    }

    private String extractGuid(SyndEntry entry) {
        if (entry.getUri() != null && !entry.getUri().isBlank()) {
            return entry.getUri();
        }
        if (entry.getLink() != null && !entry.getLink().isBlank()) {
            return entry.getLink();
        }
        // Fallback: use title + date hash
        return String.valueOf((entry.getTitle() + entry.getPublishedDate()).hashCode());
    }

    private String extractContent(SyndEntry entry) {
        // Try to get content first
        if (entry.getContents() != null && !entry.getContents().isEmpty()) {
            return entry.getContents().get(0).getValue();
        }
        // Fall back to description
        if (entry.getDescription() != null) {
            return entry.getDescription().getValue();
        }
        return "";
    }

    private Instant extractPublishedDate(SyndEntry entry) {
        // Try published date first (pubDate in RSS 2.0, published in Atom 1.0)
        if (entry.getPublishedDate() != null) {
            return entry.getPublishedDate().toInstant();
        }
        // Fallback to updated date (modified in Atom 0.3, updated in Atom 1.0)
        if (entry.getUpdatedDate() != null) {
            return entry.getUpdatedDate().toInstant();
        }
        // Final fallback: use current timestamp for feeds with unparseable dates
        return Instant.now();
    }
}
