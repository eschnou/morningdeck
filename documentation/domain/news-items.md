# News Items

A NewsItem represents a single piece of content (article, post, newsletter) fetched from a source and enriched with AI-generated metadata.

## Overview

News items are the core content unit in Morning Deck:
- Created when sources are fetched (RSS, Web, Email, Reddit)
- Enriched with AI: summary, topics, entities, sentiment
- Scored against the briefing criteria (0-100)
- Included in reports based on score ranking
- Users can mark items as read or saved

## Data Model

### NewsItem Entity

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/model/NewsItem.java`

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | Primary key |
| `source` | Source | Parent source (many-to-one) |
| `guid` | String(512) | Unique identifier within source |
| `title` | String(1024) | Article title |
| `link` | String(2048) | URL to original article |
| `author` | String | Author name (optional) |
| `publishedAt` | Instant | Publication date |
| `rawContent` | TEXT | Original content (HTML/text) |
| `cleanContent` | TEXT | Cleaned/normalized content |
| `webContent` | TEXT | Fetched full-page content |
| `summary` | TEXT | AI-generated summary |
| `tags` | NewsItemTags | AI-extracted metadata |
| `status` | NewsItemStatus | Processing status |
| `errorMessage` | String(1024) | Error details if failed |
| `readAt` | Instant | When user marked as read |
| `saved` | Boolean | Whether user saved the item |
| `score` | Integer | Relevance score (0-100) |
| `scoreReasoning` | String(512) | AI explanation for score |

**Unique constraint:** `(source_id, guid)` - prevents duplicate items per source

### NewsItemTags

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/model/NewsItemTags.java`

Stored as JSON via `NewsItemTagsConverter`:

| Field | Type | Description |
|-------|------|-------------|
| `topics` | List&lt;String&gt; | Main topics/themes |
| `people` | List&lt;String&gt; | Named people mentioned |
| `companies` | List&lt;String&gt; | Companies/organizations |
| `technologies` | List&lt;String&gt; | Technologies/products |
| `sentiment` | String | Overall sentiment |

### Status Lifecycle

```
┌────────────────────────────────────────────────────────────────────┐
│                                                                     │
│   ┌─────┐     ┌─────────┐     ┌────────────┐     ┌──────┐         │
│   │ NEW │────►│ PENDING │────►│ PROCESSING │────►│ DONE │         │
│   └─────┘     └─────────┘     └────────────┘     └──────┘         │
│                                      │                             │
│                                      ▼                             │
│                                 ┌────────┐                         │
│                                 │ ERROR  │                         │
│                                 └────────┘                         │
│                                                                     │
└────────────────────────────────────────────────────────────────────┘
```

| Status | Description |
|--------|-------------|
| `NEW` | Just arrived from fetch, waiting to be scheduled |
| `PENDING` | Queued for AI processing |
| `PROCESSING` | Worker is currently processing |
| `DONE` | Successfully processed, ready for reports |
| `ERROR` | Processing failed |

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/model/NewsItemStatus.java`

## Content Variants

News items have three content fields, used in priority order:

1. **webContent** - Full page content fetched from the article URL
2. **cleanContent** - Cleaned/normalized version of rawContent
3. **rawContent** - Original content from the source feed

The system prefers `webContent` > `cleanContent` > `rawContent` for AI processing.

### Web Content Fetching

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/queue/ProcessingWorker.java:141-159`

Web content is fetched when:
- Item has a valid HTTP/HTTPS link
- Existing content is less than 2000 characters

```java
// ProcessingWorker.java:32
private static final int CONTENT_LENGTH_THRESHOLD = 2000;
```

This improves AI enrichment quality for items with thin feed content.

## Processing Pipeline

### 1. Fetching (FetchWorker)

When a source is fetched:
- New items are created with status `NEW`
- On **first import**: items are marked `DONE` immediately (skips AI processing for historical data)
- On subsequent fetches: items remain `NEW` for processing

### 2. Scheduling (ProcessingSchedulerJob)

The scheduler runs every 60 seconds:
- Finds items with status `NEW`
- Filters by user credit availability
- Updates status to `PENDING`
- Enqueues to `ProcessingQueue`

### 3. Processing (ProcessingWorker)

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/queue/ProcessingWorker.java`

The worker:

1. Transitions status: `PENDING` → `PROCESSING`
2. Optionally fetches web content if existing content is thin
3. Sets `AiUsageContext` with user ID for tracking
4. Calls `AiService.enrichWithScore()` with:
   - Title
   - Content (clean or raw)
   - Web content (if fetched)
   - Briefing criteria
5. Stores AI results:
   - `summary` - Generated summary
   - `tags` - Topics, entities, sentiment
   - `score` - Relevance score (0-100)
   - `scoreReasoning` - Explanation for score
6. Deducts 1 credit
7. Transitions status: `PROCESSING` → `DONE` (or `ERROR`)
8. Indexes in Meilisearch (if enabled)

## Scoring

Items are scored 0-100 against the briefing criteria:

| Score Range | Meaning |
|-------------|---------|
| 80-100 | Highly relevant, likely to appear in reports |
| 50-79 | Moderately relevant |
| 20-49 | Marginally relevant |
| 0-19 | Not relevant to briefing criteria |

The score and reasoning are stored for transparency and debugging.

## User Interactions

### Read Status

- `readAt` timestamp indicates when the item was read
- Toggle via `NewsItemService.toggleRead()`
- Auto-marked read after viewing in UI (3 second delay)
- Bulk operations: `markAllAsReadBySource()`, `markAllAsReadByBriefing()`

### Saved Status

- `saved` boolean indicates user-saved items
- Toggle via `NewsItemService.toggleSaved()`
- Saved items can be filtered in the feed

## Search & Filtering

Items can be searched and filtered by:
- **Source** - Items from a specific source
- **Briefing** - Items from any source in a briefing
- **Read status** - Unread, read, or all
- **Saved status** - Saved items only
- **Minimum score** - Filter by relevance threshold
- **Full-text search** - Via Meilisearch (if enabled)

## Deduplication

Items are deduplicated by `(source_id, guid)`:
- Each source type generates GUIDs differently:
  - RSS: Uses feed item's GUID or link
  - Email: Generated from email message ID
  - Web: Generated from extracted URL
  - Reddit: Reddit post ID

## Configuration

```properties
# Processing scheduler
application.jobs.processing.enabled=true
application.jobs.processing.interval=60000

# Content threshold for web fetching
# Items with less content trigger web fetch
# (hardcoded: 2000 characters)
```

## Key Files Reference

| Component | File Path |
|-----------|-----------|
| NewsItem Entity | `backend/.../core/model/NewsItem.java` |
| NewsItemStatus Enum | `backend/.../core/model/NewsItemStatus.java` |
| NewsItemTags | `backend/.../core/model/NewsItemTags.java` |
| NewsItemTagsConverter | `backend/.../core/util/NewsItemTagsConverter.java` |
| NewsItemService | `backend/.../core/service/NewsItemService.java` |
| NewsItemRepository | `backend/.../core/repository/NewsItemRepository.java` |
| ProcessingWorker | `backend/.../core/queue/ProcessingWorker.java` |
| ProcessingSchedulerJob | `backend/.../core/job/ProcessingSchedulerJob.java` |
| WebContentFetcher | `backend/.../provider/webfetch/WebContentFetcher.java` |

## Related Documentation

- [Sources](./sources.md) - How items are fetched
- [Briefings](./briefings.md) - How items are included in reports
- [Credits](./credits.md) - Processing costs credits
- [AI Integration](../architecture/ai-integration.md) - Enrichment and scoring
- [Search](../architecture/search.md) - Meilisearch indexing
- [Queue System](../architecture/queue-system.md) - ProcessingQueue details
