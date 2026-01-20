# Search (Meilisearch)

Morning Deck uses Meilisearch for instant full-text search across news items. Meilisearch is **optional** - the system falls back to PostgreSQL queries when disabled.

## Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        ProcessingWorker                                      │
│                    (indexes after AI enrichment)                            │
└───────────────────────────────────┬─────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                      MeilisearchSyncService                                  │
│              indexNewsItem(), updateNewsItem(), deleteNewsItem()            │
└───────────────────────────────────┬─────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Meilisearch Index                                    │
│                      "news_items" (filterable)                              │
└───────────────────────────────────┬─────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                      MeilisearchSearchService                                │
│              search(), searchWithFilters(), searchByBrief()                 │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Search Document

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/search/NewsItemSearchDocument.java`

Maps `NewsItem` to a flat structure for indexing:

| Field | Type | Purpose |
|-------|------|---------|
| `id` | String | Primary key (UUID) |
| `title` | String | Searchable |
| `summary` | String | Searchable |
| `content` | String | Searchable (truncated to 10K chars) |
| `author` | String | Searchable |
| `tags_topics` | String[] | Searchable |
| `tags_people` | String[] | Searchable |
| `tags_companies` | String[] | Searchable |
| `tags_technologies` | String[] | Searchable |
| `user_id` | String | **Security filter** (required) |
| `brief_id` | String | **Security filter** |
| `source_id` | String | Filter |
| `source_name` | String | Display |
| `is_read` | boolean | Filter |
| `saved` | boolean | Filter |
| `score` | Integer | Filter/sort |
| `sentiment` | String | Filter |
| `published_at` | Long | Sort (epoch seconds) |
| `created_at` | Long | Sort (epoch seconds) |

### Security Requirement

**CRITICAL:** All searches MUST include `user_id` in the filter to ensure users only see their own items:

```java
String filter = "user_id = '" + userId.toString() + "'";
```

## Sync Service

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/search/MeilisearchSyncService.java`

### Index Operations

| Method | Trigger | Purpose |
|--------|---------|---------|
| `indexNewsItem()` | After AI processing | Add new item to index |
| `updateNewsItem()` | User action (read/saved) | Update item in index |
| `deleteNewsItem()` | Item deletion | Remove from index |

All operations are `@Async` to avoid blocking the main flow.

### Batch Operations

| Method | Purpose |
|--------|---------|
| `reindexBrief(briefId)` | Reindex all items for a brief |
| `reindexAll()` | Full reindex (disaster recovery) |
| `deleteByUserId(userId)` | Remove user's items on account deletion |
| `deleteByBriefId(briefId)` | Remove brief's items on deletion |

## Search Service

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/search/MeilisearchSearchService.java`

Provides search queries with security filters:

```java
public Page<NewsItemSearchDocument> searchByBrief(
    UUID userId,
    UUID briefId,
    String query,
    Boolean isRead,
    Boolean saved,
    Integer minScore,
    UUID sourceId,
    Pageable pageable
)
```

### Filter Building

```java
List<String> filters = new ArrayList<>();
filters.add("user_id = '" + userId + "'");  // ALWAYS required
filters.add("brief_id = '" + briefId + "'");

if (isRead != null) {
    filters.add("is_read = " + isRead);
}
if (saved != null) {
    filters.add("saved = " + saved);
}
if (minScore != null) {
    filters.add("score >= " + minScore);
}

String filterString = String.join(" AND ", filters);
```

## Index Configuration

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/search/MeilisearchIndexService.java`

Sets up the index with:

### Searchable Attributes

```java
String[] searchableAttributes = {
    "title",
    "summary",
    "content",
    "author",
    "tags_topics",
    "tags_people",
    "tags_companies",
    "tags_technologies"
};
```

### Filterable Attributes

```java
String[] filterableAttributes = {
    "user_id",      // Required for security
    "brief_id",
    "source_id",
    "is_read",
    "saved",
    "score",
    "sentiment",
    "published_at",
    "created_at"
};
```

### Sortable Attributes

```java
String[] sortableAttributes = {
    "published_at",
    "created_at",
    "score"
};
```

## Fallback Behavior

When Meilisearch is disabled (`meilisearch.enabled=false`):
- Search services are not instantiated
- `NewsItemService` falls back to PostgreSQL LIKE queries
- Functionality works but with slower/less accurate search

## Configuration

```properties
# Enable/disable Meilisearch
meilisearch.enabled=true

# Meilisearch connection
meilisearch.host=http://localhost:7700
meilisearch.api-key=your-master-key

# Index name
meilisearch.index-name=news_items
```

## Health Indicator

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/search/MeilisearchHealthIndicator.java`

Reports Meilisearch status in `/actuator/health`:

```json
{
  "meilisearch": {
    "status": "UP",
    "details": {
      "version": "1.12.0"
    }
  }
}
```

## Integration Points

### ProcessingWorker

After AI enrichment completes:

```java
// Index in Meilisearch if enabled
if (meilisearchSyncService != null) {
    meilisearchSyncService.indexNewsItem(item);
}
```

### NewsItemService

When user marks read/saved:

```java
// Update search index
if (meilisearchSyncService != null) {
    meilisearchSyncService.updateNewsItem(item);
}
```

## Key Files Reference

| Component | File Path |
|-----------|-----------|
| NewsItemSearchDocument | `backend/.../core/search/NewsItemSearchDocument.java` |
| MeilisearchSyncService | `backend/.../core/search/MeilisearchSyncService.java` |
| MeilisearchSearchService | `backend/.../core/search/MeilisearchSearchService.java` |
| MeilisearchIndexService | `backend/.../core/search/MeilisearchIndexService.java` |
| MeilisearchHealthIndicator | `backend/.../core/search/MeilisearchHealthIndicator.java` |
| ArticleSearchRequest | `backend/.../core/search/ArticleSearchRequest.java` |
| MeilisearchConfig | `backend/.../config/MeilisearchConfig.java` |
| MeilisearchProperties | `backend/.../config/MeilisearchProperties.java` |

## Related Documentation

- [News Items](../domain/news-items.md) - What gets indexed
- [Configuration](../operations/configuration.md) - Meilisearch settings
- [Local Setup](../development/local-setup.md) - Running Meilisearch locally
