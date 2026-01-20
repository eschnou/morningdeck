package be.transcode.morningdeck.server.provider.sourcefetch;

import be.transcode.morningdeck.server.core.exception.SourceFetchException;
import be.transcode.morningdeck.server.core.model.Source;
import be.transcode.morningdeck.server.core.model.SourceType;
import be.transcode.morningdeck.server.core.util.HtmlToMarkdownConverter;
import be.transcode.morningdeck.server.provider.ai.AiService;
import be.transcode.morningdeck.server.provider.ai.AiUsageContext;
import be.transcode.morningdeck.server.provider.ai.model.ExtractedWebItem;
import be.transcode.morningdeck.server.provider.sourcefetch.model.FetchedItem;
import be.transcode.morningdeck.server.provider.sourcefetch.model.SourceValidationResult;
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
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebFetcher implements SourceFetcher {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_CONTENT_SIZE = 100_000; // 100KB
    private static final Pattern TITLE_PATTERN = Pattern.compile("<title[^>]*>([^<]+)</title>", Pattern.CASE_INSENSITIVE);

    private final HtmlToMarkdownConverter htmlToMarkdownConverter;
    private final AiService aiService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public SourceType getSourceType() {
        return SourceType.WEB;
    }

    @Override
    public SourceValidationResult validate(String url) {
        try {
            String html = fetchHtml(url);
            String title = extractTitle(html);
            return SourceValidationResult.success(
                    title != null ? title : "Web Page",
                    "Web page source"
            );
        } catch (Exception e) {
            log.warn("Failed to validate web URL {}: {}", url, e.getMessage());
            return SourceValidationResult.failure("Failed to fetch URL: " + e.getMessage());
        }
    }

    @Override
    public List<FetchedItem> fetch(Source source, Instant lastFetchedAt) {
        // Set user context for usage tracking
        UUID userId = source.getDayBrief().getUserId();
        try {
            AiUsageContext.setUserId(userId);

            // Fetch HTML
            String html = fetchHtml(source.getUrl());

            // Convert to markdown
            String markdown = htmlToMarkdownConverter.convert(html);

            // Truncate if too large
            if (markdown.length() > MAX_CONTENT_SIZE) {
                markdown = markdown.substring(0, MAX_CONTENT_SIZE);
                log.info("Truncated content for source {} from {} to {} chars",
                        source.getId(), markdown.length(), MAX_CONTENT_SIZE);
            }

            // Extract items using AI
            List<ExtractedWebItem> extractedItems = aiService.extractFromWeb(
                    markdown, source.getExtractionPrompt());

            if (extractedItems == null || extractedItems.isEmpty()) {
                log.info("No items extracted from source {}", source.getId());
                return List.of();
            }

            // Map to FetchedItem
            List<FetchedItem> items = new ArrayList<>();
            for (ExtractedWebItem extracted : extractedItems) {
                if (extracted.link() == null || extracted.link().isBlank()) {
                    log.debug("Skipping item without link: {}", extracted.title());
                    continue;
                }

                // Resolve relative URLs
                String absoluteLink = UrlNormalizer.resolveRelative(source.getUrl(), extracted.link());

                // Use normalized link as GUID
                String guid = UrlNormalizer.normalize(absoluteLink);

                FetchedItem item = FetchedItem.builder()
                        .guid(guid)
                        .title(extracted.title())
                        .link(absoluteLink)
                        .cleanContent(extracted.content())
                        .publishedAt(Instant.now())
                        .build();

                items.add(item);
            }

            log.info("Extracted {} items from web source {}", items.size(), source.getId());
            return items;

        } catch (Exception e) {
            throw new SourceFetchException("Failed to fetch web source: " + e.getMessage(), e);
        } finally {
            AiUsageContext.clear();
        }
    }

    private String fetchHtml(String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .header("User-Agent", "MorningDeck/1.0 (+https://morningdeck.com)")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IOException("HTTP error: " + response.statusCode());
            }

            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        }
    }

    private String extractTitle(String html) {
        if (html == null) {
            return null;
        }
        Matcher matcher = TITLE_PATTERN.matcher(html);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }
}
