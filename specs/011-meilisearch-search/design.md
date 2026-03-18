# Meilisearch Search - Technical Design

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                         Frontend                                 │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  SearchInput (debounced 150ms)                          │    │
│  │  GET /api/daybriefs/{id}/items?q=...&filters...         │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Spring Boot Backend                         │
│                                                                  │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐       │
│  │ DayBrief     │───▶│ Meilisearch  │───▶│ Meilisearch  │       │
│  │ Controller   │    │ SearchService│    │ Server       │       │
│  └──────────────┘    └──────────────┘    └──────────────┘       │
│         │                                                        │
│         │ (when meilisearch.enabled=false or q is empty)        │
│         ▼                                                        │
│  ┌──────────────┐                                               │
│  │ Existing     │  (no search, just filters)                    │
│  │ Repository   │                                               │
│  └──────────────┘                                               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Index Sync (Async)                           │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐       │
│  │ Domain       │───▶│ Sync         │───▶│ Meilisearch  │       │
│  │ Events       │    │ Service      │    │ Index        │       │
│  └──────────────┘    └──────────────┘    └──────────────┘       │
│                                                                  │
│  Only active when meilisearch.enabled=true                      │
└─────────────────────────────────────────────────────────────────┘
```

## Feature Flag Behavior

| `meilisearch.enabled` | Search with `q` param | Search without `q` param |
|-----------------------|----------------------|-------------------------|
| `false` (default)     | Returns empty or ignores `q` | Normal filter-based listing |
| `true`                | Meilisearch instant search | Normal filter-based listing |

**Self-hosted deployments** can simply omit Meilisearch configuration - the app works identically, just without instant search.

## Meilisearch Index Design

### Index Name
```
news_items
```

### Document Structure

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",

  // Searchable text fields
  "title": "Apple Announces New AI Features",
  "summary": "Apple revealed new artificial intelligence capabilities...",
  "content": "Full article content here...",
  "author": "John Doe",
  "tags_topics": ["artificial intelligence", "technology"],
  "tags_people": ["Tim Cook"],
  "tags_companies": ["Apple", "OpenAI"],
  "tags_technologies": ["machine learning", "neural networks"],

  // Filterable metadata (CRITICAL for security & features)
  "user_id": "user-uuid-here",
  "brief_id": "brief-uuid-here",
  "source_id": "source-uuid-here",
  "source_name": "TechCrunch",

  // Filter fields
  "is_read": false,
  "saved": true,
  "score": 85,
  "sentiment": "positive",

  // Timestamps (for sorting & filtering)
  "published_at": 1704067200,
  "read_at": null,
  "created_at": 1704067200
}
```

### Index Configuration

```java
// Searchable attributes (order = priority)
searchableAttributes = [
    "title",          // Highest priority
    "summary",
    "tags_topics",
    "tags_people",
    "tags_companies",
    "tags_technologies",
    "author",
    "content"         // Lowest priority (large field)
]

// Filterable attributes (for WHERE-like conditions)
filterableAttributes = [
    "user_id",        // CRITICAL: tenant isolation
    "brief_id",       // Scope to brief
    "source_id",      // Source filter
    "is_read",        // Read/unread filter
    "saved",          // Saved filter
    "score",          // Score threshold
    "published_at",   // Date range
    "sentiment"       // Sentiment filter
]

// Sortable attributes
sortableAttributes = [
    "published_at",
    "score",
    "created_at"
]

// Ranking rules (relevance tuning)
rankingRules = [
    "words",          // Number of query words matched
    "typo",           // Fewer typos = higher rank
    "proximity",      // Words closer together = higher rank
    "attribute",      // Match in title > match in content
    "sort",           // User-specified sort
    "exactness"       // Exact matches rank higher
]
```

## Backend Components

### 1. Configuration

```java
// application.yml
meilisearch:
  enabled: true                              // Feature flag
  host: ${MEILI_HOST:http://localhost:7700}
  api-key: ${MEILI_API_KEY:}
  index-name: news_items
  connection-timeout: 5000
  read-timeout: 5000
```

```java
@ConfigurationProperties(prefix = "meilisearch")
public class MeilisearchProperties {
    private boolean enabled = false;
    private String host;
    private String apiKey;
    private String indexName = "news_items";
    private int connectionTimeout = 5000;
    private int readTimeout = 5000;
}
```

### 2. Meilisearch Client Bean

```java
@Configuration
@ConditionalOnProperty(name = "meilisearch.enabled", havingValue = "true")
public class MeilisearchConfig {

    @Bean
    public Client meilisearchClient(MeilisearchProperties props) {
        Config config = new Config(props.getHost(), props.getApiKey());
        return new Client(config);
    }

    @Bean
    public Index newsItemsIndex(Client client, MeilisearchProperties props) {
        return client.index(props.getIndexName());
    }
}
```

### 3. Search Document DTO

```java
@Data
public class NewsItemSearchDocument {
    private String id;

    // Searchable
    private String title;
    private String summary;
    private String content;
    private String author;
    private List<String> tagsTopics;
    private List<String> tagsPeople;
    private List<String> tagsCompanies;
    private List<String> tagsTechnologies;

    // Filterable metadata
    private String userId;
    private String briefId;
    private String sourceId;
    private String sourceName;

    // Filter fields
    private boolean isRead;
    private boolean saved;
    private Integer score;
    private String sentiment;

    // Timestamps (as epoch seconds for Meilisearch)
    private Long publishedAt;
    private Long readAt;
    private Long createdAt;

    public static NewsItemSearchDocument from(NewsItem item) {
        // Map entity to document
    }
}
```

### 4. Search Service

```java
@Service
@ConditionalOnProperty(name = "meilisearch.enabled", havingValue = "true")
public class MeilisearchSearchService {

    private final Index index;

    public SearchResultsPage search(ArticleSearchRequest request) {
        // Build filter string
        String filter = buildFilter(request);

        com.meilisearch.sdk.SearchRequest meiliRequest =
            com.meilisearch.sdk.SearchRequest.builder()
                .q(request.getQuery())
                .filter(new String[]{filter})
                .sort(new String[]{buildSort(request)})
                .offset(request.getPage() * request.getSize())
                .limit(request.getSize())
                .build();

        Searchable result = index.search(meiliRequest);

        return mapToPage(result, request);
    }

    private String buildFilter(ArticleSearchRequest req) {
        List<String> filters = new ArrayList<>();

        // CRITICAL: Always filter by user for security
        filters.add("user_id = '" + req.getUserId() + "'");

        // Scope to brief
        filters.add("brief_id = '" + req.getBriefId() + "'");

        // Optional filters
        if (req.getReadStatus() != null) {
            filters.add("is_read = " + "READ".equals(req.getReadStatus()));
        }
        if (req.getSaved() != null) {
            filters.add("saved = " + req.getSaved());
        }
        if (req.getMinScore() != null) {
            filters.add("score >= " + req.getMinScore());
        }
        if (req.getSourceId() != null) {
            filters.add("source_id = '" + req.getSourceId() + "'");
        }

        return String.join(" AND ", filters);
    }

    private String buildSort(ArticleSearchRequest req) {
        // Default: published_at:desc
        return "published_at:desc";
    }
}
```

### 5. Index Sync Service

```java
@Service
@ConditionalOnProperty(name = "meilisearch.enabled", havingValue = "true")
public class MeilisearchSyncService {

    private final Index index;

    @Async
    public void indexNewsItem(NewsItem item) {
        NewsItemSearchDocument doc = NewsItemSearchDocument.from(item);
        index.addDocuments(List.of(doc));
    }

    @Async
    public void updateNewsItem(NewsItem item) {
        NewsItemSearchDocument doc = NewsItemSearchDocument.from(item);
        index.updateDocuments(List.of(doc));
    }

    @Async
    public void deleteNewsItem(UUID id) {
        index.deleteDocument(id.toString());
    }

    public void reindexAll(UUID userId) {
        // Batch reindex all items for a user
    }

    public void reindexBrief(UUID briefId) {
        // Batch reindex all items in a brief
    }
}
```

### 6. Entity Listener for Auto-Sync

```java
@Component
@ConditionalOnProperty(name = "meilisearch.enabled", havingValue = "true")
public class NewsItemIndexListener {

    private final MeilisearchSyncService syncService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNewsItemCreated(NewsItemCreatedEvent event) {
        syncService.indexNewsItem(event.getNewsItem());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNewsItemUpdated(NewsItemUpdatedEvent event) {
        syncService.updateNewsItem(event.getNewsItem());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNewsItemDeleted(NewsItemDeletedEvent event) {
        syncService.deleteNewsItem(event.getNewsItemId());
    }
}
```

## Frontend Integration

### Search Input Component

```tsx
// No changes needed to API contract
// Existing endpoint already supports `q` parameter:
// GET /api/daybriefs/{id}/items?q=searchterm&readStatus=UNREAD&minScore=50

const useArticleSearch = (briefId: string) => {
  const [query, setQuery] = useState('');
  const debouncedQuery = useDebounce(query, 150); // 150ms debounce

  const { data, isLoading } = useQuery({
    queryKey: ['articles', briefId, debouncedQuery, filters],
    queryFn: () => apiClient.get(`/daybriefs/${briefId}/items`, {
      params: { q: debouncedQuery, ...filters }
    }),
    enabled: debouncedQuery.length >= 2 || debouncedQuery === ''
  });

  return { query, setQuery, results: data, isLoading };
};
```

## Infrastructure

### Docker Compose (Development)

```yaml
# docker-compose.yml
services:
  postgres:
    # ... existing config

  meilisearch:
    image: getmeili/meilisearch:v1.12
    container_name: morningdeck-meilisearch
    environment:
      - MEILI_MASTER_KEY=${MEILI_MASTER_KEY:-devMasterKey12345}
      - MEILI_NO_ANALYTICS=true
      - MEILI_ENV=development
    ports:
      - "7700:7700"
    volumes:
      - meilisearch_data:/meili_data
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:7700/health"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  postgres_data:
  meilisearch_data:
```

### Environment Variables

```bash
# .env.local (development)
MEILI_HOST=http://localhost:7700
MEILI_API_KEY=devMasterKey12345
MEILI_ENABLED=true

# Production
MEILI_HOST=http://localhost:7700  # Via nginx proxy
MEILI_API_KEY=<secure-production-key>
MEILI_ENABLED=true
```

## Security Considerations

### Tenant Isolation

Every search query MUST include user_id filter:

```java
// NEVER skip this - data leakage vulnerability
String filter = "user_id = '" + currentUser.getId() + "'";
```

### API Key Management

- Development: Use master key directly
- Production: Generate search-only API key with restricted permissions
- Never expose master key to frontend

### Index Access Control

```java
// On startup, configure index to restrict document access
TaskInfo task = index.updateFilterableAttributes(
    new String[]{"user_id", "brief_id", ...}
);
```

## Failure Handling

When Meilisearch is unavailable at runtime:
- Search requests with `q` param will return an error (503 Service Unavailable)
- Regular listing (without `q`) continues to work via PostgreSQL
- Health check will report Meilisearch as DOWN

### Health Indicator

```java
@Component
@ConditionalOnProperty(name = "meilisearch.enabled", havingValue = "true")
public class MeilisearchHealthIndicator implements HealthIndicator {

    private final Client client;

    @Override
    public Health health() {
        try {
            client.health();
            return Health.up()
                .withDetail("status", "operational")
                .build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

## Testing Strategy

### Unit Tests
- Mock Meilisearch client
- Test filter string building
- Test document mapping

### Integration Tests (Testcontainers)
```java
@SpringBootTest
@Testcontainers
class MeilisearchIntegrationTest {

    @Container
    static MeilisearchContainer meilisearch = new MeilisearchContainer(
        DockerImageName.parse("getmeili/meilisearch:v1.12"))
        .withMasterKey("testKey");

    @Test
    void shouldSearchWithinBrief() { ... }

    @Test
    void shouldNotLeakDataBetweenUsers() { ... }

    @Test
    void shouldRespectFilters() { ... }
}
```

### Feature Flag Tests
- Test app starts normally when `meilisearch.enabled=false`
- Test search endpoint behavior when disabled (returns empty or 501)
- Test normal listing works regardless of flag
