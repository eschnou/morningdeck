# Sources

Sources are the primary mechanism for importing content into briefings. Each source type implements the `SourceFetcher` interface, providing a unified abstraction for validation and fetching while allowing type-specific behavior.

## Supported Source Types

| Type | Description | Fetch Mode |
|------|-------------|------------|
| **RSS** | Standard RSS/Atom feed polling | Pull (scheduled) |
| **WEB** | AI-powered extraction from web pages | Pull (scheduled) |
| **EMAIL** | Push-based email newsletter ingestion | Push (event-driven) |
| **REDDIT** | Reddit subreddit content via OAuth API | Pull (scheduled) |

## Core Abstraction

### SourceFetcher Interface

**File:** `backend/src/main/java/be/transcode/morningdeck/server/provider/sourcefetch/SourceFetcher.java`

```java
public interface SourceFetcher {
    SourceType getSourceType();
    SourceValidationResult validate(String url);
    List<FetchedItem> fetch(Source source, Instant lastFetchedAt);
}
```

All source implementations are Spring `@Component` beans. The `SourceService` receives `List<SourceFetcher>` via injection and dispatches to the correct implementation based on `SourceType`.

### Source Entity

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/model/Source.java`

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | Primary key |
| `type` | SourceType | RSS, EMAIL, WEB, REDDIT |
| `url` | String(2048) | Source URL (or `email://{uuid}` for EMAIL) |
| `emailAddress` | UUID | For EMAIL sources only |
| `extractionPrompt` | String(2048) | For WEB sources only |
| `status` | SourceStatus | ACTIVE, PAUSED, ERROR |
| `fetchStatus` | FetchStatus | IDLE, QUEUED, FETCHING |
| `lastFetchedAt` | Instant | Last successful fetch |
| `refreshIntervalMinutes` | Integer | Polling interval (0 for EMAIL) |
| `etag` / `lastModified` | String | HTTP caching for RSS |

### FetchedItem DTO

**File:** `backend/src/main/java/be/transcode/morningdeck/server/provider/sourcefetch/model/FetchedItem.java`

Intermediate representation returned by fetchers before conversion to `NewsItem`:

```java
public record FetchedItem(
    String guid,          // Unique ID per source
    String title,
    String link,
    String author,
    Instant publishedAt,
    String rawContent,    // Original HTML/text
    String cleanContent   // Markdown-converted
) {}
```

## Source Implementations

### RSS Sources (RssFetcher)

**File:** `backend/src/main/java/be/transcode/morningdeck/server/provider/sourcefetch/RssFetcher.java`

Standard RSS/Atom feed polling using the Rome library.

**Key behaviors:**
- 30-second HTTP timeout
- HTTP caching via ETag and If-Modified-Since headers (lines 104-109)
- HTML-to-Markdown conversion for content
- Skips items older than `lastFetchedAt`
- Returns empty list on 304 Not Modified

**Validation:** Fetches and parses feed, returns title/description.

### Web Sources (WebFetcher)

**File:** `backend/src/main/java/be/transcode/morningdeck/server/provider/sourcefetch/WebFetcher.java`

AI-powered extraction from arbitrary web pages.

**Key behaviors:**
- Fetches entire page HTML
- Truncates content to 100KB before AI processing (lines 80-84)
- Uses `AiService.extractFromWeb()` with user-defined `extractionPrompt`
- Resolves relative URLs to absolute (line 104)
- Sets `publishedAt` to current time (no historical dates)

**Validation:** Tests URL reachability, extracts page title.

**Required fields:** `url` and `extractionPrompt`

### Email Sources (EmailSourceFetcher)

**File:** `backend/src/main/java/be/transcode/morningdeck/server/provider/sourcefetch/EmailSourceFetcher.java`

Push-based newsletter ingestion. This is a **no-op fetcher** - emails arrive via events.

**Key behaviors:**
- `validate()` always succeeds
- `fetch()` returns empty list
- `refreshIntervalMinutes` set to 0

**Email ingestion flow:**
1. External email provider publishes `EmailReceivedEvent`
2. `EmailIngestionListener` processes the event
3. AI extracts news items via `AiService.extractFromEmail()`
4. Creates `NewsItem` entities with status NEW

**Email Ingestion Listener:**
**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/service/EmailIngestionListener.java`

### Reddit Sources (RedditFetcher)

**File:** `backend/src/main/java/be/transcode/morningdeck/server/provider/sourcefetch/reddit/RedditFetcher.java`

Reddit subreddit content via OAuth2 API.

**Key behaviors:**
- URL format: `reddit://subreddit_name`
- Validates subreddit name (2-21 chars, alphanumeric + underscores)
- Filters posts: excludes self-posts, stickied, NSFW, Reddit-hosted media
- Fetches "hot" posts with configurable limits
- Access token caching with 60-second refresh buffer (lines 203-233)

**Validation:** Tests subreddit accessibility.

**Configuration:** Requires `application.reddit.client-id` and related OAuth properties.

## Data Flow

### Pull-Based Sources (RSS, WEB, Reddit)

```
┌──────────────┐    ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│  Scheduler   │───►│  FetchWorker │───►│SourceFetcher │───►│  NewsItem    │
│              │    │  (Queue)     │    │  .fetch()    │    │  (Database)  │
└──────────────┘    └──────────────┘    └──────────────┘    └──────────────┘
                                                                    │
                                                                    ▼
                                                            ┌──────────────┐
                                                            │ Processing   │
                                                            │ Worker (AI)  │
                                                            └──────────────┘
```

1. **Scheduling:** Periodic job queries sources due for refresh via `SourceRepository.findSourcesDueForRefresh()`
2. **Fetching:** `FetchWorker` calls `SourceFetcher.fetch()`, maps `FetchedItem` → `NewsItem`
3. **Processing:** `ProcessingWorker` enriches items with AI (summary, score, tags)

**FetchWorker:** `backend/src/main/java/be/transcode/morningdeck/server/core/queue/FetchWorker.java`
**ProcessingWorker:** `backend/src/main/java/be/transcode/morningdeck/server/core/queue/ProcessingWorker.java`

### Push-Based Sources (Email)

```
┌──────────────┐    ┌──────────────────────┐    ┌──────────────┐
│ Email Event  │───►│ EmailIngestionListener│───►│  NewsItem    │
│              │    │ (AiService.extract)   │    │  (Database)  │
└──────────────┘    └──────────────────────┘    └──────────────┘
                                                        │
                                                        ▼
                                                ┌──────────────┐
                                                │ Processing   │
                                                │ Worker (AI)  │
                                                └──────────────┘
```

## Adding a New Source Type

To add a new source type:

### 1. Add to SourceType Enum

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/model/SourceType.java`

```java
public enum SourceType {
    RSS,
    EMAIL,
    WEB,
    REDDIT,
    NEW_TYPE  // Add here
}
```

### 2. Implement SourceFetcher

Create a new class implementing `SourceFetcher`:

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class NewTypeFetcher implements SourceFetcher {

    @Override
    public SourceType getSourceType() {
        return SourceType.NEW_TYPE;
    }

    @Override
    public SourceValidationResult validate(String url) {
        // Validate URL format and accessibility
        // Return SourceValidationResult.success(title, description) or .failure(message)
    }

    @Override
    public List<FetchedItem> fetch(Source source, Instant lastFetchedAt) {
        // Fetch content and return list of FetchedItem
        // Skip items older than lastFetchedAt
    }
}
```

### 3. Update SourceService

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/service/SourceService.java`

Add type-specific creation logic in `createSource()`:

```java
case NEW_TYPE -> {
    // Validate URL format
    // Call fetcher.validate()
    // Set default refreshIntervalMinutes
    // Set type-specific fields if any
}
```

### 4. Add Source Entity Fields (if needed)

If the new type requires specific fields:

1. Add column to `Source.java` entity
2. Create Flyway migration: `resources/db/migrations/V{next}__add_new_type_source.sql`
3. Update `SourceDTO` and mapper

### 5. Update Frontend

1. Add type to `SourceType` in `frontend/src/types/index.ts`
2. Update `AddSourceDialog` with type selector and fields
3. Update `SourceCard` with appropriate icon
4. Update `SourceDetailPage` to display type-specific information

## Key Files Reference

| Component | File Path |
|-----------|-----------|
| SourceFetcher Interface | `backend/.../provider/sourcefetch/SourceFetcher.java` |
| RssFetcher | `backend/.../provider/sourcefetch/RssFetcher.java` |
| WebFetcher | `backend/.../provider/sourcefetch/WebFetcher.java` |
| EmailSourceFetcher | `backend/.../provider/sourcefetch/EmailSourceFetcher.java` |
| RedditFetcher | `backend/.../provider/sourcefetch/reddit/RedditFetcher.java` |
| Source Entity | `backend/.../core/model/Source.java` |
| SourceType Enum | `backend/.../core/model/SourceType.java` |
| SourceService | `backend/.../core/service/SourceService.java` |
| FetchWorker | `backend/.../core/queue/FetchWorker.java` |
| ProcessingWorker | `backend/.../core/queue/ProcessingWorker.java` |
| EmailIngestionListener | `backend/.../core/service/EmailIngestionListener.java` |
| SourceRepository | `backend/.../core/repository/SourceRepository.java` |
| UrlNormalizer | `backend/.../provider/sourcefetch/UrlNormalizer.java` |
| FetchedItem DTO | `backend/.../provider/sourcefetch/model/FetchedItem.java` |
| SourceValidationResult | `backend/.../provider/sourcefetch/model/SourceValidationResult.java` |

## Related Documentation

- [Queue System](../architecture/queue-system.md) - How sources are fetched via queues
- [News Items](./news-items.md) - What happens after items are fetched
- [Email Infrastructure](../architecture/email-infrastructure.md) - Email source details
