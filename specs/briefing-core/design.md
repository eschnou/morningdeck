# Briefing-Core Design Document

## Overview

Refactor the domain model to make Briefing the aggregate root. Sources become children of Briefing (many-to-one), enabling inline relevance scoring during news item processing. Reports become simple queries over pre-scored items.

**Key changes:**
- Source belongs to exactly one Briefing (remove many-to-many)
- NewsItem stores score + reasoning (computed during enrichment)
- AiService combines enrichment + scoring in single LLM call
- BriefingWorker generates reports from pre-scored items (no LLM)
- UI reorganized around briefing-centric navigation

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Frontend                                 │
│  ┌─────────────┐  ┌─────────────────┐  ┌──────────────────┐    │
│  │ BriefsPage  │  │ BriefDetailPage │  │ BriefItemsPage   │    │
│  │ (list)      │  │ (detail+sources)│  │ (items+filters)  │    │
│  └─────────────┘  └─────────────────┘  └──────────────────┘    │
└────────────────────────────┬────────────────────────────────────┘
                             │ REST API
┌────────────────────────────▼────────────────────────────────────┐
│                         Backend                                  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ Controllers                                                │  │
│  │  DayBriefController (briefs, sources, items, reports)     │  │
│  └───────────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ Services                                                   │  │
│  │  DayBriefService, SourceService, NewsItemService          │  │
│  └───────────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ Processing Pipeline                                        │  │
│  │  FetchWorker → ProcessingWorker (enrich+score) → Done     │  │
│  └───────────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ AI Provider                                                │  │
│  │  AiService.enrichWithScore(title, content, criteria)      │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## Components and Interfaces

### Backend Changes

#### 1. Model: Source (updated)

Remove `@ManyToMany` with DayBrief. Add `@ManyToOne` to DayBrief.

```java
@Entity
@Table(name = "sources")
public class Source {
    // ... existing fields ...

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "day_brief_id", nullable = false)
    private DayBrief dayBrief;

    // Remove: Set<DayBrief> dayBriefs
}
```

#### 2. Model: DayBrief (updated)

Replace `@ManyToMany sources` with `@OneToMany`.

```java
@Entity
@Table(name = "day_briefs")
public class DayBrief {
    // ... existing fields ...

    @OneToMany(mappedBy = "dayBrief", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Source> sources = new ArrayList<>();

    // Remove: @ManyToMany with join table
}
```

#### 3. Model: NewsItem (updated)

Add score and scoreReasoning fields.

```java
@Entity
@Table(name = "news_items")
public class NewsItem {
    // ... existing fields ...

    @Column(name = "score")
    private Integer score;  // 0-100, nullable

    @Column(name = "score_reasoning", length = 512)
    private String scoreReasoning;
}
```

#### 4. AiService Interface (updated)

Add combined enrich+score method.

```java
public interface AiService {
    // Existing method - for items without briefing context
    EnrichmentResult enrich(String title, String content);

    // New method - enrich + score in single call
    EnrichmentWithScoreResult enrichWithScore(String title, String content, String briefingCriteria);

    // Remove: score() method (no longer needed separately)
}
```

#### 5. New DTO: EnrichmentWithScoreResult

```java
public record EnrichmentWithScoreResult(
    String summary,
    List<String> topics,
    EntitiesResult entities,
    String sentiment,
    Integer score,         // 0-100
    String scoreReasoning
) {}
```

#### 6. ProcessingWorker (updated)

Fetch briefing criteria from NewsItem → Source → DayBrief chain.

```java
@Component
public class ProcessingWorker {
    public void process(UUID newsItemId) {
        NewsItem item = newsItemRepository.findById(newsItemId).orElse(null);
        // ... status checks ...

        String briefingCriteria = item.getSource().getDayBrief().getBriefing();
        String content = item.getCleanContent() != null ? item.getCleanContent() : item.getRawContent();

        EnrichmentWithScoreResult result = aiService.enrichWithScore(
            item.getTitle(),
            content,
            briefingCriteria
        );

        item.setSummary(result.summary());
        item.setTags(/* from result */);
        item.setScore(result.score());
        item.setScoreReasoning(result.scoreReasoning());
        item.setStatus(NewsItemStatus.DONE);
        newsItemRepository.save(item);
    }
}
```

#### 7. BriefingWorker (simplified)

No LLM calls - just query pre-scored items.

```java
@Component
public class BriefingWorker {
    private DailyReportDTO doExecuteBriefing(DayBrief dayBrief) {
        LocalDateTime since = dayBrief.getLastExecutedAt() != null
            ? dayBrief.getLastExecutedAt()
            : LocalDateTime.now().minusDays(1);

        // Query pre-scored items - no LLM calls
        List<NewsItem> items = newsItemRepository.findTopScoredItems(
            dayBrief.getId(),
            NewsItemStatus.DONE,
            since,
            MAX_REPORT_ITEMS
        );

        // Create report from results
        DailyReport report = createReport(dayBrief, items);
        return mapToDTO(report);
    }
}
```

#### 8. SourceService (updated)

Add briefingId parameter to createSource.

```java
@Service
public class SourceService {
    public SourceDTO createSource(UUID userId, UUID briefingId, String url, ...) {
        DayBrief dayBrief = dayBriefRepository.findById(briefingId)
            .orElseThrow(() -> new ResourceNotFoundException("Briefing not found"));

        if (!dayBrief.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Briefing not found");
        }

        Source source = Source.builder()
            .dayBrief(dayBrief)
            .url(url)
            // ... other fields
            .build();

        return sourceRepository.save(source);
    }
}
```

#### 9. NewsItemRepository (new query)

```java
public interface NewsItemRepository extends JpaRepository<NewsItem, UUID> {
    // New: Get top scored items for a briefing
    @Query("""
        SELECT n FROM NewsItem n
        WHERE n.source.dayBrief.id = :briefingId
          AND n.status = :status
          AND n.publishedAt > :since
          AND n.score IS NOT NULL
        ORDER BY n.score DESC
        """)
    List<NewsItem> findTopScoredItems(
        @Param("briefingId") UUID briefingId,
        @Param("status") NewsItemStatus status,
        @Param("since") LocalDateTime since,
        Pageable pageable
    );

    // New: Get items for briefing with filters
    @Query("""
        SELECT n FROM NewsItem n
        WHERE n.source.dayBrief.id = :briefingId
          AND (:sourceId IS NULL OR n.source.id = :sourceId)
          AND (:minScore IS NULL OR n.score >= :minScore)
          AND (:readStatus IS NULL OR
               (:readStatus = 'UNREAD' AND n.readAt IS NULL) OR
               (:readStatus = 'READ' AND n.readAt IS NOT NULL))
          AND (:saved IS NULL OR n.saved = :saved)
        ORDER BY n.publishedAt DESC
        """)
    Page<NewsItem> findByBriefingWithFilters(
        @Param("briefingId") UUID briefingId,
        @Param("sourceId") UUID sourceId,
        @Param("minScore") Integer minScore,
        @Param("readStatus") String readStatus,
        @Param("saved") Boolean saved,
        Pageable pageable
    );
}
```

#### 10. New Prompt: enrich-with-score.st

```
Analyze the following news article and provide:

1. summary: A 2-3 sentence summary focusing on key facts and main point
2. topics: List of 1-5 topic categories (e.g., "AI", "Finance", "Climate")
3. entities: People, companies, and technologies mentioned
4. sentiment: Overall sentiment (positive, neutral, or negative)
5. score: Relevance score 0-100 based on the user's interests below
6. scoreReasoning: Brief explanation of the score

User's interests and goals:
{briefingCriteria}

Title: {title}
Content: {content}

Score higher if the article directly relates to the stated interests.
Score lower if it's tangentially related or off-topic.
```

#### 11. API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/daybriefs` | List briefings |
| POST | `/daybriefs` | Create briefing |
| GET | `/daybriefs/{id}` | Get briefing |
| PUT | `/daybriefs/{id}` | Update briefing |
| DELETE | `/daybriefs/{id}` | Delete briefing (cascades) |
| GET | `/daybriefs/{id}/sources` | List sources for briefing |
| POST | `/daybriefs/{id}/sources` | Add source to briefing |
| GET | `/daybriefs/{id}/items` | List items with filters |
| GET | `/daybriefs/{id}/reports` | List reports |
| POST | `/daybriefs/{id}/execute` | Generate report |
| GET | `/sources` | List all sources (optional, for overview) |
| GET | `/sources/{id}` | Get source detail |
| PUT | `/sources/{id}` | Update source |
| DELETE | `/sources/{id}` | Delete source |
| POST | `/sources/{id}/refresh` | Refresh source |

### Frontend Changes

#### 1. Types (updated)

```typescript
// SourceDTO - add briefingId
export interface SourceDTO {
  id: string;
  briefingId: string;      // NEW
  briefingTitle?: string;  // NEW (for display)
  url: string;
  name: string;
  // ... rest unchanged
}

// NewsItemDTO - add score fields
export interface NewsItemDTO {
  id: string;
  score: number | null;          // NEW
  scoreReasoning: string | null; // NEW
  // ... rest unchanged
}

// CreateSourceRequest - add briefingId
export interface CreateSourceRequest {
  briefingId: string;  // NEW (required)
  url: string;
  name?: string;
  // ... rest unchanged
}
```

#### 2. API Client (updated)

```typescript
class ApiClient {
  // Sources now scoped to briefing
  async getBriefingSources(briefingId: string, page = 0, size = 20): Promise<PagedResponse<SourceDTO>> {
    return this.request(`/daybriefs/${briefingId}/sources?page=${page}&size=${size}`);
  }

  async createBriefingSource(briefingId: string, data: Omit<CreateSourceRequest, 'briefingId'>): Promise<SourceDTO> {
    return this.request(`/daybriefs/${briefingId}/sources`, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  // Items with filters
  async getBriefingItems(
    briefingId: string,
    page = 0,
    size = 20,
    filters?: {
      sourceId?: string;
      minScore?: number;
      readStatus?: ReadFilter;
      saved?: boolean;
      sortBy?: 'publishedAt' | 'score';
    }
  ): Promise<PagedResponse<NewsItemDTO>> {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (filters?.sourceId) params.append('sourceId', filters.sourceId);
    if (filters?.minScore) params.append('minScore', String(filters.minScore));
    if (filters?.readStatus && filters.readStatus !== 'ALL') params.append('readStatus', filters.readStatus);
    if (filters?.saved) params.append('saved', 'true');
    if (filters?.sortBy) params.append('sortBy', filters.sortBy);
    return this.request(`/daybriefs/${briefingId}/items?${params.toString()}`);
  }
}
```

#### 3. BriefDetailPage (redesigned)

Structure:
- Header: Brief title, status, actions (execute, edit, delete)
- Tabs:
  - **Items**: News items list with filters (source, score, read, saved, sort)
  - **Sources**: List of sources with add/manage
  - **Reports**: Historical reports

#### 4. New Component: BriefingItemsList

Replaces current SourceItemsList with briefing-scoped version.

```tsx
interface BriefingItemsListProps {
  briefingId: string;
  sources: SourceDTO[];  // For filter dropdown
}

function BriefingItemsList({ briefingId, sources }: BriefingItemsListProps) {
  const [filters, setFilters] = useState({
    sourceId: undefined,
    minScore: 0,
    readStatus: 'ALL' as ReadFilter,
    saved: undefined,
    sortBy: 'publishedAt' as 'publishedAt' | 'score',
  });

  // ... fetch items with filters, render list with score badges
}
```

#### 5. Score Display Component

```tsx
interface ScoreBadgeProps {
  score: number | null;
}

function ScoreBadge({ score }: ScoreBadgeProps) {
  if (score === null) return <Badge variant="outline">Pending</Badge>;

  const color = score >= 70 ? 'green' : score >= 40 ? 'yellow' : 'gray';
  return <Badge className={`bg-${color}-100 text-${color}-800`}>{score}</Badge>;
}
```

## Data Models

### Database Schema Changes

```sql
-- Remove join table
DROP TABLE IF EXISTS day_brief_sources;

-- Add day_brief_id to sources
ALTER TABLE sources ADD COLUMN day_brief_id UUID NOT NULL;
ALTER TABLE sources ADD CONSTRAINT fk_source_daybrief
    FOREIGN KEY (day_brief_id) REFERENCES day_briefs(id) ON DELETE CASCADE;
CREATE INDEX idx_sources_daybrief ON sources(day_brief_id);

-- Add score fields to news_items
ALTER TABLE news_items ADD COLUMN score INTEGER;
ALTER TABLE news_items ADD COLUMN score_reasoning VARCHAR(512);
CREATE INDEX idx_newsitems_score ON news_items(score) WHERE score IS NOT NULL;
```

### Entity Relationship (After)

```
User 1──┬──* DayBrief 1──┬──* Source 1──* NewsItem
        │                │
        │                └──* DailyReport 1──* ReportItem
        │
        └──* Subscription
```

## Error Handling

| Scenario | Handling |
|----------|----------|
| Scoring fails during enrichment | Log error, set score=null, continue with enrichment only |
| Briefing criteria empty/null | Skip scoring, set score=null |
| Source without briefing (edge case) | Prevent at validation; reject source creation |
| Delete briefing with sources | Cascade delete (sources → items) |

## Testing Strategy

### Unit Tests
- `ProcessingWorker`: Verify enrichWithScore called with correct briefing criteria
- `BriefingWorker`: Verify report generation uses pre-scored items
- `SourceService`: Verify briefingId required and validated

### Integration Tests
- Create briefing → add source → fetch items → verify scores populated
- Delete briefing → verify cascade to sources and items
- Filter items by score threshold

## Performance Considerations

- **Inline scoring**: Single LLM call (enrich+score) vs two separate calls — net neutral or slight improvement
- **Report generation**: Query-only, < 100ms expected
- **Item queries with filters**: Index on `(day_brief_id, score, published_at)` for common query patterns

```sql
CREATE INDEX idx_newsitems_briefing_score
ON news_items(source_id, score DESC, published_at DESC)
WHERE status = 'DONE' AND score IS NOT NULL;
```

## Security Considerations

- Source ownership via briefing: `source.dayBrief.userId == currentUser.id`
- Item access via source chain: `item.source.dayBrief.userId == currentUser.id`
- Repository queries include briefing ownership check

## Monitoring and Observability

Existing metrics remain valid. Add:
- `newsitem.score.histogram`: Distribution of scores
- `newsitem.score.null.count`: Items without scores (failed scoring)
- `report.generation.duration`: Time to generate report (should be < 1s)
