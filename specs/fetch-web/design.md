# Fetch Web Content - Design Document

## Overview

Enhance news item processing by fetching full article content from URLs before AI enrichment. This addresses the common case where RSS feeds and email newsletters provide only snippets. The fetched content is also displayed in the UI when available.

**Key design decisions:**
- Pluggable `WebContentFetcher` interface to support different implementations (HttpClient now, Playwright later)
- Simple HttpClient-based implementation using Readability4J for content extraction
- Graceful degradation: failures don't block processing
- Content stored in new `webContent` field on `NewsItem`
- Backend handles content selection in DTO mapping; frontend requires no changes

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           ProcessingWorker                                   │
│                                                                              │
│  ┌──────────────┐    ┌──────────────────┐    ┌──────────────────────────┐  │
│  │ NewsItem     │───▶│ WebContentFetcher │───▶│ AiService.enrichWithScore │  │
│  │ (link, clean │    │ (pluggable)       │    │ (title, content,          │  │
│  │  Content)    │    │                   │    │  webContent, criteria)    │  │
│  └──────────────┘    └──────────────────┘    └──────────────────────────┘  │
│                              │                                               │
│                              ▼                                               │
│                 ┌────────────────────────┐                                  │
│                 │ Implementations:        │                                  │
│                 │ - HttpClientWebFetcher  │                                  │
│                 │ - (future) Playwright   │                                  │
│                 └────────────────────────┘                                  │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Components and Interfaces

### New Interface: `WebContentFetcher`

Location: `provider/webfetch/WebContentFetcher.java`

```java
public interface WebContentFetcher {
    /**
     * Fetches and extracts article content from a URL.
     * @param url The article URL to fetch
     * @return Extracted content as markdown, or empty Optional if fetch fails
     */
    Optional<String> fetch(String url);
}
```

### New Implementation: `HttpClientWebFetcher`

Location: `provider/webfetch/HttpClientWebFetcher.java`

Responsibilities:
- Validate URL (HTTP/HTTPS only)
- Fetch HTML using Java HttpClient
- Extract article content using Readability4J
- Convert to markdown using `HtmlToMarkdownConverter`

Configuration via `application.properties`:
```properties
application.web-fetch.enabled=true
application.web-fetch.timeout-seconds=15
application.web-fetch.max-redirects=3
application.web-fetch.user-agent=DayBrief/1.0
```

Implementation sketch:
```java
@Component
@ConditionalOnProperty(prefix = "application.web-fetch", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class HttpClientWebFetcher implements WebContentFetcher {

    private final HtmlToMarkdownConverter markdownConverter;

    @Value("${application.web-fetch.timeout-seconds:15}")
    private int timeoutSeconds;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public Optional<String> fetch(String url) {
        if (!isValidUrl(url)) {
            return Optional.empty();
        }

        try {
            String html = fetchHtml(url);
            String articleHtml = extractArticle(url, html);
            String markdown = markdownConverter.convert(articleHtml);
            return Optional.of(markdown);
        } catch (Exception e) {
            log.warn("Failed to fetch web content from {}: {}", url, e.getMessage());
            return Optional.empty();
        }
    }

    private boolean isValidUrl(String url) {
        // Only HTTP/HTTPS, no localhost/private IPs (SSRF prevention)
    }

    private String extractArticle(String url, String html) {
        Readability4J readability = new Readability4J(url, html);
        Article article = readability.parse();
        return article.getContent(); // HTML of article body
    }
}
```

### New: `NoOpWebFetcher`

Location: `provider/webfetch/NoOpWebFetcher.java`

Fallback when web fetching is disabled:
```java
@Component
@ConditionalOnProperty(prefix = "application.web-fetch", name = "enabled", havingValue = "false")
public class NoOpWebFetcher implements WebContentFetcher {
    @Override
    public Optional<String> fetch(String url) {
        return Optional.empty();
    }
}
```

### Modified: `ProcessingWorker`

Location: `core/queue/ProcessingWorker.java`

Changes:
1. Inject `WebContentFetcher`
2. Before calling AI, attempt to fetch web content if conditions are met
3. Pass web content to AI service
4. Store fetched content in `NewsItem.webContent`

```java
private void doProcess(NewsItem item) {
    String content = item.getCleanContent() != null ? item.getCleanContent() : item.getRawContent();
    String webContent = null;

    // Fetch web content if applicable
    if (shouldFetchWebContent(item)) {
        webContent = webContentFetcher.fetch(item.getLink()).orElse(null);
        if (webContent != null) {
            item.setWebContent(webContent);
        }
    }

    String briefingCriteria = item.getSource().getDayBrief().getBriefing();

    // Pass both content sources to AI
    EnrichmentWithScoreResult result = aiService.enrichWithScore(
        item.getTitle(),
        content,
        webContent,
        briefingCriteria
    );

    // ... rest unchanged
}

private boolean shouldFetchWebContent(NewsItem item) {
    String link = item.getLink();

    // Skip if no link or non-HTTP
    if (link == null || (!link.startsWith("http://") && !link.startsWith("https://"))) {
        return false;
    }

    // Skip if content is already substantial (>2000 chars)
    String content = item.getCleanContent() != null ? item.getCleanContent() : item.getRawContent();
    if (content != null && content.length() > 2000) {
        return false;
    }

    return true;
}
```

### Modified: `AiService` Interface

Add overloaded method:
```java
EnrichmentWithScoreResult enrichWithScore(
    String title,
    String content,
    String webContent,  // nullable
    String briefingCriteria
);
```

### Modified: `SpringAiService`

Update to handle web content:
```java
@Override
public EnrichmentWithScoreResult enrichWithScore(String title, String content, String webContent, String briefingCriteria) {
    // Combine content intelligently
    String effectiveContent = buildEffectiveContent(content, webContent);

    return chatClient.prompt()
            .user(u -> u.text(loadPrompt(enrichWithScorePromptResource))
                    .param("title", title)
                    .param("content", truncate(effectiveContent, 4000))
                    .param("briefingCriteria", briefingCriteria))
            .call()
            .entity(EnrichmentWithScoreResult.class);
}

private String buildEffectiveContent(String original, String webContent) {
    if (webContent == null || webContent.isBlank()) {
        return original;
    }
    // Prefer web content, but include snippet as context if short
    if (original != null && original.length() < 500) {
        return "Original snippet:\n" + original + "\n\nFull article:\n" + webContent;
    }
    return webContent;
}
```

### Modified: Prompt `enrich-with-score.st`

No changes needed. The prompt already accepts `{content}` - we just provide better content.

### Modified: `NewsItemService`

Location: `core/service/NewsItemService.java`

Update `mapToDTO()` to prefer web content when available:

```java
private NewsItemDTO mapToDTO(NewsItem item) {
    return NewsItemDTO.builder()
            .id(item.getId())
            .title(item.getTitle())
            .link(item.getLink())
            .author(item.getAuthor())
            .publishedAt(item.getPublishedAt())
            .content(selectBestContent(item))  // Changed
            .summary(item.getSummary())
            .tags(item.getTags())
            .sourceId(item.getSource().getId())
            .sourceName(item.getSource().getName())
            .readAt(item.getReadAt())
            .saved(item.getSaved())
            .score(item.getScore())
            .scoreReasoning(item.getScoreReasoning())
            .createdAt(item.getCreatedAt())
            .build();
}

/**
 * Returns the best available content for display.
 * Prefers fetched web content over original feed content.
 */
private String selectBestContent(NewsItem item) {
    if (item.getWebContent() != null && !item.getWebContent().isBlank()) {
        return item.getWebContent();
    }
    return item.getCleanContent();
}
```

This ensures:
- Frontend receives best available content without any changes
- Web content (full article) is shown when available
- Falls back to original feed content otherwise

## Data Models

### Modified Entity: `NewsItem`

Add field:
```java
@Column(name = "web_content", columnDefinition = "TEXT")
private String webContent;
```

### New Migration

File: `V{next}_add_web_content_to_news_items.sql`
```sql
ALTER TABLE news_items ADD COLUMN web_content TEXT;
```

## Error Handling

| Scenario | Handling |
|----------|----------|
| Invalid URL (mailto:, ftp:, etc.) | Skip fetch, proceed with original content |
| Network timeout | Log warning, proceed with original content |
| HTTP 4xx/5xx | Log warning, proceed with original content |
| Readability extraction fails | Log warning, proceed with original content |
| Private IP/localhost (SSRF) | Reject URL, proceed with original content |

All errors are logged but never cause `NewsItem` to enter ERROR state.

## Testing Strategy

### Unit Tests

1. **`HttpClientWebFetcherTest`**
   - Valid URL returns extracted content
   - Invalid URL (mailto:) returns empty
   - Private IP rejected
   - Timeout handling
   - HTTP error handling

2. **`ProcessingWorkerTest`**
   - Calls `WebContentFetcher` when conditions met
   - Skips fetch when content > 2000 chars
   - Skips fetch for non-HTTP links
   - Handles fetch failure gracefully
   - Stores `webContent` when fetched

3. **`NewsItemServiceTest`**
   - Returns `webContent` when available
   - Falls back to `cleanContent` when `webContent` is null
   - Falls back to `cleanContent` when `webContent` is blank

### Integration Tests

1. **`WebFetchIT`** (using WireMock)
   - End-to-end fetch and extraction
   - Redirect handling
   - Various content types

## Performance Considerations

- **Timeout**: 15 second hard limit prevents slow sites from blocking the queue
- **Content threshold**: Skip fetch for articles > 2000 chars (already complete)
- **No retries**: Single attempt per item; failures proceed with original content
- **Queue unchanged**: Processing workers remain at 2 threads; web fetch is synchronous within each

## Security Considerations

### SSRF Prevention

`HttpClientWebFetcher.isValidUrl()` validates:
- Protocol: Only `http://` or `https://`
- Host: Reject `localhost`, `127.0.0.1`, `10.*`, `172.16-31.*`, `192.168.*`, `169.254.*`
- Port: Allow only 80, 443, or implicit

### Content Sanitization

- Readability4J outputs clean HTML (no scripts)
- `HtmlToMarkdownConverter` further sanitizes to markdown
- Stored content is text-only, no executable code

## Monitoring and Observability

### Logging

- `INFO`: Successful fetch with URL and content length
- `WARN`: Fetch failures with URL and reason
- `DEBUG`: Skip decisions (content too long, invalid URL)

### Metrics (future)

- `web_fetch_success_total` - Counter of successful fetches
- `web_fetch_failure_total` - Counter by failure reason
- `web_fetch_duration_seconds` - Histogram of fetch times

## Dependencies

Add to `pom.xml`:
```xml
<dependency>
    <groupId>net.dankito.readability4j</groupId>
    <artifactId>readability4j</artifactId>
    <version>1.0.8</version>
</dependency>
```

Note: Jsoup (required by Readability4J) is already present.

## File Summary

| File | Action |
|------|--------|
| `provider/webfetch/WebContentFetcher.java` | Create (interface) |
| `provider/webfetch/HttpClientWebFetcher.java` | Create |
| `provider/webfetch/NoOpWebFetcher.java` | Create |
| `core/queue/ProcessingWorker.java` | Modify |
| `provider/ai/AiService.java` | Modify (add overload) |
| `provider/ai/SpringAiService.java` | Modify |
| `provider/ai/MockAiService.java` | Modify (add overload) |
| `core/model/NewsItem.java` | Modify (add field) |
| `core/service/NewsItemService.java` | Modify (content selection in DTO) |
| `resources/db/migration/V{n}__add_web_content.sql` | Create |
| `application.properties` | Modify (add config) |
