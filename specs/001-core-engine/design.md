# Core Engine - Design Document

## 1. Overview

The Core Engine implements the news aggregation and briefing pipeline for daybrief.ai. It consists of:
- **Source Management**: CRUD operations for RSS feed sources with extensible provider pattern
- **Ingestion Engine**: Scheduled fetching and parsing of RSS feeds into NewsItem entities
- **Processing Pipeline**: AI-powered summarization, tagging, and scoring using Spring AI
- **Briefing System**: User-configurable DayBriefs with scheduled execution and report generation

## 2. Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              REST API Layer                                  │
│  SourceController  │  NewsController  │  DayBriefController                 │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
┌─────────────────────────────────────────────────────────────────────────────┐
│                             Service Layer                                    │
│  SourceService  │  NewsItemService  │  DayBriefService  │  ReportService    │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
┌──────────────────────┬──────────────────────┬───────────────────────────────┐
│   Ingestion Engine   │  Processing Pipeline │      Briefing Executor        │
│  ┌────────────────┐  │  ┌────────────────┐  │  ┌─────────────────────────┐  │
│  │ FeedFetcher    │  │  │ NewsProcessor  │  │  │ BriefingScheduler       │  │
│  │ (Scheduler)    │  │  │ (Scheduler)    │  │  │ BriefingExecutor        │  │
│  └────────────────┘  │  └────────────────┘  │  └─────────────────────────┘  │
│          │           │          │           │              │                │
│  ┌────────────────┐  │  ┌────────────────┐  │                               │
│  │ SourceFetcher  │  │  │ AI Services    │  │                               │
│  │ (Interface)    │  │  │ (Spring AI)    │  │                               │
│  └────────────────┘  │  └────────────────┘  │                               │
│    ▲                 │                      │                               │
│    │                 │                      │                               │
│  ┌────────────────┐  │                      │                               │
│  │ RssFetcher     │  │                      │                               │
│  │ (Rome lib)     │  │                      │                               │
│  └────────────────┘  │                      │                               │
└──────────────────────┴──────────────────────┴───────────────────────────────┘
                                    │
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Repository Layer                                   │
│  SourceRepository │ NewsItemRepository │ DayBriefRepository │ ReportRepo    │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
┌─────────────────────────────────────────────────────────────────────────────┐
│                              PostgreSQL                                      │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 3. Components and Interfaces

### 3.1 Source Fetching Abstraction

```java
// provider/sourcefetch/SourceFetcher.java
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
     * @param source The source entity
     * @param lastFetchedAt Only fetch items newer than this (null for first fetch)
     * @return List of raw feed items
     */
    List<FetchedItem> fetch(Source source, LocalDateTime lastFetchedAt);
}

// provider/sourcefetch/model/SourceValidationResult.java
@Data
@Builder
public class SourceValidationResult {
    private boolean valid;
    private String feedTitle;        // Auto-detected title
    private String feedDescription;  // Auto-detected description
    private String errorMessage;     // If invalid
}

// provider/sourcefetch/model/FetchedItem.java
@Data
@Builder
public class FetchedItem {
    private String guid;
    private String title;
    private String link;
    private String author;
    private LocalDateTime publishedAt;
    private String rawContent;       // Original HTML/text
    private String cleanContent;     // Stripped text
}
```

### 3.2 RSS Fetcher Implementation

```java
// provider/sourcefetch/RssFetcher.java
@Component
@RequiredArgsConstructor
public class RssFetcher implements SourceFetcher {

    @Override
    public SourceType getSourceType() {
        return SourceType.RSS;
    }

    @Override
    public SourceValidationResult validate(String url) {
        // Use Rome library to validate and extract feed metadata
    }

    @Override
    public List<FetchedItem> fetch(Source source, LocalDateTime lastFetchedAt) {
        // Use Rome library with HTTP caching (ETag, Last-Modified)
        // Parse items and convert to FetchedItem
    }
}
```

### 3.3 AI Processing Services

Each AI task has its own dedicated service with specific input/output records for structured output.

```java
// provider/ai/AiService.java
public interface AiService {
    /**
     * Generate a summary for a news item
     */
    SummaryResult summarize(String title, String content);

    /**
     * Extract tags from a news item
     */
    TagsResult extractTags(String title, String content, String summary);

    /**
     * Score a news item against briefing criteria
     */
    ScoreResult score(String title, String summary, String briefingCriteria);
}
```

#### AI Output Records (for Spring AI structured output)

```java
// provider/ai/model/SummaryResult.java
@JsonPropertyOrder({"summary"})
public record SummaryResult(
    @JsonProperty("summary") String summary  // 2-3 sentences
) {}

// provider/ai/model/TagsResult.java
@JsonPropertyOrder({"topics", "entities", "sentiment"})
public record TagsResult(
    @JsonProperty("topics") List<String> topics,
    @JsonProperty("entities") EntitiesResult entities,
    @JsonProperty("sentiment") String sentiment  // positive, neutral, negative
) {}

// provider/ai/model/EntitiesResult.java
@JsonPropertyOrder({"people", "companies", "technologies"})
public record EntitiesResult(
    @JsonProperty("people") List<String> people,
    @JsonProperty("companies") List<String> companies,
    @JsonProperty("technologies") List<String> technologies
) {}

// provider/ai/model/ScoreResult.java
@JsonPropertyOrder({"score", "reasoning"})
public record ScoreResult(
    @JsonProperty("score") Integer score,      // 0-100
    @JsonProperty("reasoning") String reasoning // Brief explanation
) {}
```

### 3.4 Prompt Resources

Prompts are stored as text files in `src/main/resources/prompts/` for easy maintenance and versioning.

```
src/main/resources/prompts/
├── summarize.st
├── extract-tags.st
└── score-relevance.st
```

```text
// prompts/summarize.st
Summarize the following news article in 2-3 concise sentences.
Focus on the key facts and main point.

Title: {title}
Content: {content}
```

```text
// prompts/extract-tags.st
Analyze the following news article and extract:
- topics: List of 1-5 topic categories (e.g., "AI", "Finance", "Climate")
- entities: People, companies, and technologies mentioned
- sentiment: Overall sentiment (positive, neutral, or negative)

Title: {title}
Content: {content}
Summary: {summary}
```

```text
// prompts/score-relevance.st
Score the relevance of this news article (0-100) based on the user's interests.

User's interests and goals:
{briefingCriteria}

Article title: {title}
Article summary: {summary}

Score higher if the article directly relates to the stated interests.
Score lower if it's tangentially related or off-topic.
Provide brief reasoning for the score.
```

### 3.5 Spring AI Implementation

```java
// provider/ai/SpringAiService.java
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "application.ai", name = "provider", havingValue = "openai")
public class SpringAiService implements AiService {

    private final ChatClient chatClient;

    @Value("classpath:prompts/summarize.st")
    private Resource summarizePromptResource;

    @Value("classpath:prompts/extract-tags.st")
    private Resource extractTagsPromptResource;

    @Value("classpath:prompts/score-relevance.st")
    private Resource scoreRelevancePromptResource;

    @Override
    public SummaryResult summarize(String title, String content) {
        return chatClient.prompt()
            .user(u -> u.text(loadPrompt(summarizePromptResource))
                .param("title", title)
                .param("content", truncate(content, 4000)))
            .call()
            .entity(SummaryResult.class);
    }

    @Override
    public TagsResult extractTags(String title, String content, String summary) {
        return chatClient.prompt()
            .user(u -> u.text(loadPrompt(extractTagsPromptResource))
                .param("title", title)
                .param("content", truncate(content, 2000))
                .param("summary", summary))
            .call()
            .entity(TagsResult.class);
    }

    @Override
    public ScoreResult score(String title, String summary, String briefingCriteria) {
        return chatClient.prompt()
            .user(u -> u.text(loadPrompt(scoreRelevancePromptResource))
                .param("title", title)
                .param("summary", summary)
                .param("briefingCriteria", briefingCriteria))
            .call()
            .entity(ScoreResult.class);
    }

    private String loadPrompt(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load prompt resource: " + resource.getFilename(), e);
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
```

### 3.6 Core Services

```java
// core/service/SourceService.java
@Service
@RequiredArgsConstructor
public class SourceService {
    private final SourceRepository sourceRepository;
    private final List<SourceFetcher> sourceFetchers;  // Injected fetchers

    public Source createSource(UUID userId, String url, String name,
                               SourceType type, List<String> tags);
    public Source getSource(UUID userId, UUID sourceId);
    public Page<Source> listSources(UUID userId, SourceStatus status, Pageable pageable);
    public Source updateSource(UUID userId, UUID sourceId, String name,
                               List<String> tags, SourceStatus status);
    public void deleteSource(UUID userId, UUID sourceId);  // Soft delete
    public SourceValidationResult validateSource(String url, SourceType type);
}

// core/service/NewsItemService.java
@Service
@RequiredArgsConstructor
public class NewsItemService {
    private final NewsItemRepository newsItemRepository;

    public NewsItem getNewsItem(UUID userId, UUID newsItemId);
    public Page<NewsItem> searchNewsItems(UUID userId, String query,
                                          UUID sourceId, LocalDateTime from,
                                          LocalDateTime to, Pageable pageable);
}

// core/service/DayBriefService.java
@Service
@RequiredArgsConstructor
public class DayBriefService {
    private final DayBriefRepository dayBriefRepository;
    private final SourceRepository sourceRepository;

    public DayBrief createDayBrief(UUID userId, String title, String description,
                                   String briefing, List<UUID> sourceIds,
                                   BriefingFrequency frequency, LocalTime scheduleTime,
                                   String timezone);
    public DayBrief getDayBrief(UUID userId, UUID dayBriefId);
    public Page<DayBrief> listDayBriefs(UUID userId, DayBriefStatus status, Pageable pageable);
    public DayBrief updateDayBrief(UUID userId, UUID dayBriefId, ...);
    public void deleteDayBrief(UUID userId, UUID dayBriefId);  // Soft delete
}

// core/service/ReportService.java
@Service
@RequiredArgsConstructor
public class ReportService {
    private final DailyReportRepository reportRepository;

    public DailyReport getReport(UUID userId, UUID dayBriefId, UUID reportId);
    public Page<DailyReport> listReports(UUID userId, UUID dayBriefId, Pageable pageable);
}
```

### 3.7 Background Jobs

```java
// core/job/FeedIngestionJob.java
@Component
@RequiredArgsConstructor
@Slf4j
public class FeedIngestionJob {
    private final SourceRepository sourceRepository;
    private final NewsItemRepository newsItemRepository;
    private final List<SourceFetcher> sourceFetchers;

    @Scheduled(fixedRateString = "${application.jobs.feed-ingestion.interval:900000}")
    public void fetchFeeds() {
        List<Source> activeSources = sourceRepository.findByStatus(SourceStatus.ACTIVE);
        for (Source source : activeSources) {
            try {
                fetchSource(source);
            } catch (Exception e) {
                log.error("Failed to fetch source {}: {}", source.getId(), e.getMessage());
                source.setStatus(SourceStatus.ERROR);
                source.setLastError(e.getMessage());
                sourceRepository.save(source);
            }
        }
    }

    private void fetchSource(Source source) {
        SourceFetcher fetcher = findFetcher(source.getType());
        List<FetchedItem> items = fetcher.fetch(source, source.getLastFetchedAt());

        for (FetchedItem item : items) {
            if (!newsItemRepository.existsBySourceIdAndGuid(source.getId(), item.getGuid())) {
                NewsItem newsItem = mapToNewsItem(source, item);
                newsItemRepository.save(newsItem);
            }
        }

        source.setLastFetchedAt(LocalDateTime.now());
        source.setLastError(null);
        sourceRepository.save(source);
    }
}

// core/job/NewsProcessingJob.java
@Component
@RequiredArgsConstructor
@Slf4j
public class NewsProcessingJob {
    private final NewsItemRepository newsItemRepository;
    private final AiService aiService;

    @Scheduled(fixedRateString = "${application.jobs.news-processing.interval:60000}")
    public void processNews() {
        List<NewsItem> pendingItems = newsItemRepository
            .findByStatusInOrderByCreatedAtAsc(
                List.of(NewsItemStatus.PENDING, NewsItemStatus.SUMMARIZED),
                PageRequest.of(0, 10)
            );

        for (NewsItem item : pendingItems) {
            processItem(item);
        }
    }

    private void processItem(NewsItem item) {
        try {
            if (item.getStatus() == NewsItemStatus.PENDING) {
                SummaryResult summary = aiService.summarize(item.getTitle(), item.getCleanContent());
                item.setSummary(summary.summary());
                item.setStatus(NewsItemStatus.SUMMARIZED);
                newsItemRepository.save(item);
            }

            if (item.getStatus() == NewsItemStatus.SUMMARIZED) {
                TagsResult tags = aiService.extractTags(
                    item.getTitle(), item.getCleanContent(), item.getSummary());
                item.setTags(mapToJson(tags));
                item.setStatus(NewsItemStatus.PROCESSED);
                newsItemRepository.save(item);
            }
        } catch (Exception e) {
            log.error("Failed to process item {}: {}", item.getId(), e.getMessage());
            item.setRetryCount(item.getRetryCount() + 1);
            if (item.getRetryCount() >= 3) {
                item.setStatus(NewsItemStatus.ERROR);
                item.setErrorMessage(e.getMessage());
            }
            newsItemRepository.save(item);
        }
    }
}

// core/job/BriefingExecutionJob.java
@Component
@RequiredArgsConstructor
@Slf4j
public class BriefingExecutionJob {
    private final DayBriefRepository dayBriefRepository;
    private final NewsItemRepository newsItemRepository;
    private final DailyReportRepository reportRepository;
    private final ReportItemRepository reportItemRepository;
    private final AiService aiService;

    @Scheduled(cron = "0 * * * * *")  // Every minute
    public void checkAndExecuteBriefings() {
        LocalDateTime now = LocalDateTime.now();
        List<DayBrief> dueBriefings = dayBriefRepository.findDueBriefings(
            DayBriefStatus.ACTIVE, now);

        for (DayBrief briefing : dueBriefings) {
            try {
                executeBriefing(briefing);
            } catch (Exception e) {
                log.error("Failed to execute briefing {}: {}", briefing.getId(), e.getMessage());
            }
        }
    }

    @Transactional
    public DailyReport executeBriefing(DayBrief dayBrief) {
        // 1. Get news items from linked sources since last execution
        LocalDateTime since = dayBrief.getLastExecutedAt() != null
            ? dayBrief.getLastExecutedAt()
            : LocalDateTime.now().minusDays(1);

        List<NewsItem> candidates = newsItemRepository
            .findBySourceIdInAndStatusAndPublishedAtAfter(
                dayBrief.getSourceIds(),
                NewsItemStatus.PROCESSED,
                since
            );

        // 2. Score each item against briefing criteria
        List<ScoredItem> scoredItems = candidates.stream()
            .map(item -> {
                ScoreResult score = aiService.score(
                    item.getTitle(),
                    item.getSummary(),
                    dayBrief.getBriefing()
                );
                return new ScoredItem(item, score.score(), score.reasoning());
            })
            .sorted(Comparator.comparing(ScoredItem::score).reversed())
            .limit(10)
            .toList();

        // 3. Create report with top items
        DailyReport report = DailyReport.builder()
            .dayBrief(dayBrief)
            .generatedAt(LocalDateTime.now())
            .status(ReportStatus.GENERATED)
            .build();
        report = reportRepository.save(report);

        // 4. Create report items
        int position = 1;
        for (ScoredItem scored : scoredItems) {
            ReportItem reportItem = ReportItem.builder()
                .report(report)
                .newsItem(scored.item())
                .score(scored.score())
                .position(position++)
                .build();
            reportItemRepository.save(reportItem);
        }

        // 5. Update briefing last executed
        dayBrief.setLastExecutedAt(LocalDateTime.now());
        dayBriefRepository.save(dayBrief);

        return report;
    }

    private record ScoredItem(NewsItem item, int score, String reasoning) {}
}
```

## 4. Data Models

### 4.1 Entities

```java
// core/model/Source.java
@Entity
@Table(name = "sources", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "url"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Source {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 2048)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SourceStatus status = SourceStatus.ACTIVE;

    @Column(columnDefinition = "TEXT[]")
    private List<String> tags;

    @Column(name = "last_fetched_at")
    private LocalDateTime lastFetchedAt;

    @Column(name = "last_error", length = 1024)
    private String lastError;

    @Column(name = "etag")
    private String etag;  // For HTTP caching

    @Column(name = "last_modified")
    private String lastModified;  // For HTTP caching

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

// core/model/SourceType.java
public enum SourceType {
    RSS
    // Future: NEWSLETTER, WEBSITE, API
}

// core/model/SourceStatus.java
public enum SourceStatus {
    ACTIVE,
    PAUSED,
    ERROR,
    DELETED
}

// core/model/NewsItem.java
@Entity
@Table(name = "news_items", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"source_id", "guid"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    @Column(nullable = false, length = 512)
    private String guid;

    @Column(nullable = false, length = 1024)
    private String title;

    @Column(nullable = false, length = 2048)
    private String link;

    @Column
    private String author;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "raw_content", columnDefinition = "TEXT")
    private String rawContent;

    @Column(name = "clean_content", columnDefinition = "TEXT")
    private String cleanContent;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private NewsItemTags tags;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private NewsItemStatus status = NewsItemStatus.PENDING;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

// core/model/NewsItemTags.java (Embeddable for JSON)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsItemTags {
    private List<String> topics;
    private List<String> people;
    private List<String> companies;
    private List<String> technologies;
    private String sentiment;
}

// core/model/NewsItemStatus.java
public enum NewsItemStatus {
    PENDING,
    SUMMARIZED,
    PROCESSED,
    ERROR
}

// core/model/DayBrief.java
@Entity
@Table(name = "day_briefs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DayBrief {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String title;

    @Column(length = 1024)
    private String description;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String briefing;  // User's goals/interests for AI filtering

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BriefingFrequency frequency;

    @Column(name = "schedule_time", nullable = false)
    private LocalTime scheduleTime;

    @Column(nullable = false)
    @Builder.Default
    private String timezone = "UTC";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DayBriefStatus status = DayBriefStatus.ACTIVE;

    @Column(name = "last_executed_at")
    private LocalDateTime lastExecutedAt;

    @ManyToMany
    @JoinTable(
        name = "day_brief_sources",
        joinColumns = @JoinColumn(name = "day_brief_id"),
        inverseJoinColumns = @JoinColumn(name = "source_id")
    )
    private Set<Source> sources = new HashSet<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public List<UUID> getSourceIds() {
        return sources.stream().map(Source::getId).toList();
    }
}

// core/model/BriefingFrequency.java
public enum BriefingFrequency {
    DAILY,
    WEEKLY
}

// core/model/DayBriefStatus.java
public enum DayBriefStatus {
    ACTIVE,
    PAUSED,
    DELETED
}

// core/model/DailyReport.java
@Entity
@Table(name = "daily_reports")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyReport {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "day_brief_id", nullable = false)
    private DayBrief dayBrief;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReportStatus status = ReportStatus.PENDING;

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<ReportItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

// core/model/ReportStatus.java
public enum ReportStatus {
    PENDING,
    GENERATED,
    ERROR
}

// core/model/ReportItem.java
@Entity
@Table(name = "report_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private DailyReport report;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "news_item_id", nullable = false)
    private NewsItem newsItem;

    @Column(nullable = false)
    private Integer score;

    @Column(nullable = false)
    private Integer position;
}
```

### 4.2 DTOs

```java
// core/dto/SourceDTO.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SourceDTO {
    private UUID id;

    @NotBlank(message = "URL is required")
    private String url;

    private String name;
    private SourceType type;
    private SourceStatus status;
    private List<String> tags;
    private LocalDateTime lastFetchedAt;
    private String lastError;
    private Long itemCount;  // Response only
    private LocalDateTime createdAt;
}

// core/dto/NewsItemDTO.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NewsItemDTO {
    private UUID id;
    private String title;
    private String link;
    private String author;
    private LocalDateTime publishedAt;
    private String content;      // cleanContent for detail view
    private String summary;
    private NewsItemTags tags;
    private UUID sourceId;
    private String sourceName;   // Response only
    private LocalDateTime createdAt;
}

// core/dto/DayBriefDTO.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DayBriefDTO {
    private UUID id;

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotBlank(message = "Briefing criteria is required")
    private String briefing;

    @NotEmpty(message = "At least one source is required")
    private List<UUID> sourceIds;

    @NotNull(message = "Frequency is required")
    private BriefingFrequency frequency;

    @NotNull(message = "Schedule time is required")
    private LocalTime scheduleTime;

    private String timezone;
    private DayBriefStatus status;
    private LocalDateTime lastExecutedAt;
    private Integer sourceCount;  // Response only
    private LocalDateTime createdAt;
}

// core/dto/DailyReportDTO.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DailyReportDTO {
    private UUID id;
    private UUID dayBriefId;
    private String dayBriefTitle;     // Response only
    private String dayBriefDescription; // Response only
    private LocalDateTime generatedAt;
    private ReportStatus status;
    private List<ReportItemDTO> items;
    private Integer itemCount;         // For list view
}

// core/dto/ReportItemDTO.java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReportItemDTO {
    private UUID newsItemId;
    private String title;
    private String summary;
    private String link;
    private LocalDateTime publishedAt;
    private Integer score;
    private Integer position;
    private String sourceName;
}
```

## 5. Error Handling

### 5.1 Custom Exceptions

```java
// core/exception/SourceValidationException.java
public class SourceValidationException extends BadRequestException {
    public SourceValidationException(String message) {
        super(message);
    }
}

// core/exception/DuplicateSourceException.java
public class DuplicateSourceException extends BadRequestException {
    public DuplicateSourceException(String url) {
        super("Source with URL already exists: " + url);
    }
}

// core/exception/SourceFetchException.java
public class SourceFetchException extends RuntimeException {
    public SourceFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}

// core/exception/AiProcessingException.java
public class AiProcessingException extends RuntimeException {
    public AiProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

### 5.2 Exception Handler Updates

Add to GlobalExceptionHandler:

```java
@ExceptionHandler(SourceValidationException.class)
public ResponseEntity<ApiError> handleSourceValidation(
        SourceValidationException ex, HttpServletRequest request) {
    return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
}

@ExceptionHandler(DuplicateSourceException.class)
public ResponseEntity<ApiError> handleDuplicateSource(
        DuplicateSourceException ex, HttpServletRequest request) {
    return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
}
```

## 6. Testing Strategy

### 6.1 Unit Tests

| Component | Test Class | Focus |
|-----------|------------|-------|
| RssFetcher | RssFetcherTest | Feed parsing, validation, HTTP caching |
| SpringAiService | SpringAiServiceTest | Mock ChatClient, verify prompts |
| SourceService | SourceServiceTest | CRUD operations, ownership checks |
| NewsProcessingJob | NewsProcessingJobTest | Pipeline transitions, retry logic |
| BriefingExecutionJob | BriefingExecutionJobTest | Scoring, selection, report creation |

### 6.2 Integration Tests

| Test Class | Scope |
|------------|-------|
| SourceIT | Full source CRUD via API |
| NewsItemIT | News item retrieval and search |
| DayBriefIT | DayBrief CRUD and source linking |
| ReportIT | Report generation and retrieval |
| FeedIngestionIT | End-to-end feed fetch with mock RSS |

### 6.3 Test Data

Use WireMock for external RSS feeds. Sample test feed:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0">
  <channel>
    <title>Test Feed</title>
    <item>
      <guid>test-item-1</guid>
      <title>Test Article</title>
      <link>https://example.com/article</link>
      <pubDate>Mon, 01 Jan 2024 00:00:00 GMT</pubDate>
      <description>Test content</description>
    </item>
  </channel>
</rss>
```

## 7. Performance Considerations

### 7.1 Database Indexes

```sql
-- Source queries
CREATE INDEX idx_sources_user_status ON sources(user_id, status);
CREATE INDEX idx_sources_status ON sources(status);

-- NewsItem queries
CREATE INDEX idx_news_items_source_guid ON news_items(source_id, guid);
CREATE INDEX idx_news_items_source_published ON news_items(source_id, published_at DESC);
CREATE INDEX idx_news_items_status ON news_items(status);
CREATE INDEX idx_news_items_created ON news_items(created_at);

-- Full text search
CREATE INDEX idx_news_items_title_content_gin ON news_items
    USING GIN (to_tsvector('english', title || ' ' || COALESCE(clean_content, '')));

-- DayBrief queries
CREATE INDEX idx_day_briefs_user_status ON day_briefs(user_id, status);
CREATE INDEX idx_day_briefs_schedule ON day_briefs(status, schedule_time);

-- Report queries
CREATE INDEX idx_daily_reports_daybrief ON daily_reports(day_brief_id, generated_at DESC);
CREATE INDEX idx_report_items_report ON report_items(report_id, position);
```

### 7.2 Job Configuration

```yaml
application:
  jobs:
    feed-ingestion:
      interval: 900000  # 15 minutes
      batch-size: 50
    news-processing:
      interval: 60000   # 1 minute
      batch-size: 10
    briefing-execution:
      cron: "0 * * * * *"  # Every minute check
```

### 7.3 Connection Pooling

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
```

## 8. Security Considerations

### 8.1 URL Validation (SSRF Prevention)

```java
// core/util/UrlValidator.java
@Component
public class UrlValidator {

    private static final Set<String> ALLOWED_PROTOCOLS = Set.of("http", "https");
    private static final Set<String> BLOCKED_HOSTS = Set.of(
        "localhost", "127.0.0.1", "0.0.0.0", "169.254.169.254"
    );

    public void validate(String urlString) {
        try {
            URL url = new URL(urlString);

            if (!ALLOWED_PROTOCOLS.contains(url.getProtocol().toLowerCase())) {
                throw new SourceValidationException("Only HTTP/HTTPS protocols allowed");
            }

            String host = url.getHost().toLowerCase();
            if (BLOCKED_HOSTS.contains(host) || host.endsWith(".internal")) {
                throw new SourceValidationException("Invalid host");
            }

            // Check for private IP ranges
            InetAddress address = InetAddress.getByName(host);
            if (address.isLoopbackAddress() || address.isSiteLocalAddress()) {
                throw new SourceValidationException("Private IP addresses not allowed");
            }
        } catch (MalformedURLException | UnknownHostException e) {
            throw new SourceValidationException("Invalid URL: " + e.getMessage());
        }
    }
}
```

### 8.2 Ownership Enforcement

All service methods that access user data must verify ownership:

```java
private Source getSourceWithOwnershipCheck(UUID userId, UUID sourceId) {
    Source source = sourceRepository.findById(sourceId)
        .orElseThrow(() -> new ResourceNotFoundException("Source not found"));

    if (!source.getUserId().equals(userId)) {
        throw new AccessDeniedException("Access denied to source");
    }

    return source;
}
```

### 8.3 Rate Limiting

```java
// config/RateLimitConfig.java
@Configuration
public class RateLimitConfig {

    @Bean
    public RateLimiter sourceCreationLimiter() {
        return RateLimiter.of("source-creation",
            RateLimiterConfig.custom()
                .limitForPeriod(50)
                .limitRefreshPeriod(Duration.ofDays(1))
                .build());
    }
}
```

## 9. Monitoring and Observability

### 9.1 Metrics

```java
// core/metrics/CoreEngineMetrics.java
@Component
@RequiredArgsConstructor
public class CoreEngineMetrics {
    private final MeterRegistry registry;

    public void recordFeedFetch(String sourceId, boolean success, long durationMs) {
        registry.counter("feed.fetch",
            "source_id", sourceId,
            "success", String.valueOf(success))
            .increment();
        registry.timer("feed.fetch.duration").record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordNewsProcessing(String stage, boolean success) {
        registry.counter("news.processing",
            "stage", stage,
            "success", String.valueOf(success))
            .increment();
    }

    public void recordBriefingExecution(String dayBriefId, int itemCount) {
        registry.counter("briefing.execution", "day_brief_id", dayBriefId).increment();
        registry.gauge("briefing.items", itemCount);
    }
}
```

### 9.2 Logging

```java
// Structured logging for jobs
@Slf4j
public class FeedIngestionJob {

    private void fetchSource(Source source) {
        log.info("Fetching source source_id={} url={}", source.getId(), source.getUrl());
        // ... fetch logic
        log.info("Fetched source source_id={} items_count={}", source.getId(), items.size());
    }
}
```

### 9.3 Health Checks

```java
// config/HealthConfig.java
@Component
public class FeedIngestionHealthIndicator implements HealthIndicator {

    @Autowired
    private SourceRepository sourceRepository;

    @Override
    public Health health() {
        long errorSources = sourceRepository.countByStatus(SourceStatus.ERROR);
        long totalSources = sourceRepository.count();

        if (errorSources > totalSources * 0.5) {
            return Health.down()
                .withDetail("error_sources", errorSources)
                .withDetail("total_sources", totalSources)
                .build();
        }

        return Health.up()
            .withDetail("error_sources", errorSources)
            .withDetail("total_sources", totalSources)
            .build();
    }
}
```

## 10. Configuration

### 10.1 Application Properties

```yaml
# application.yml additions
application:
  ai:
    provider: openai  # or anthropic
  jobs:
    feed-ingestion:
      enabled: true
      interval: 900000
    news-processing:
      enabled: true
      interval: 60000
    briefing-execution:
      enabled: true

spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        model: gpt-4o-mini
```

### 10.2 Dependencies (pom.xml additions)

```xml
<!-- RSS Parsing -->
<dependency>
    <groupId>com.rometools</groupId>
    <artifactId>rome</artifactId>
    <version>2.1.0</version>
</dependency>

<!-- Spring AI -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
</dependency>

<!-- Scheduling -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-quartz</artifactId>
</dependency>
```

## 11. Migration Script

```sql
-- V4__Core_engine_init.sql

CREATE TABLE sources (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(255) NOT NULL,
    url VARCHAR(2048) NOT NULL,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    tags TEXT[],
    last_fetched_at TIMESTAMP,
    last_error VARCHAR(1024),
    etag VARCHAR(255),
    last_modified VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    UNIQUE(user_id, url)
);

CREATE TABLE news_items (
    id UUID PRIMARY KEY,
    source_id UUID NOT NULL REFERENCES sources(id),
    guid VARCHAR(512) NOT NULL,
    title VARCHAR(1024) NOT NULL,
    link VARCHAR(2048) NOT NULL,
    author VARCHAR(255),
    published_at TIMESTAMP,
    raw_content TEXT,
    clean_content TEXT,
    summary TEXT,
    tags JSONB,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    error_message VARCHAR(1024),
    retry_count INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    UNIQUE(source_id, guid)
);

CREATE TABLE day_briefs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    title VARCHAR(255) NOT NULL,
    description VARCHAR(1024),
    briefing TEXT NOT NULL,
    frequency VARCHAR(50) NOT NULL,
    schedule_time TIME NOT NULL,
    timezone VARCHAR(100) NOT NULL DEFAULT 'UTC',
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    last_executed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE TABLE day_brief_sources (
    day_brief_id UUID NOT NULL REFERENCES day_briefs(id) ON DELETE CASCADE,
    source_id UUID NOT NULL REFERENCES sources(id) ON DELETE CASCADE,
    PRIMARY KEY (day_brief_id, source_id)
);

CREATE TABLE daily_reports (
    id UUID PRIMARY KEY,
    day_brief_id UUID NOT NULL REFERENCES day_briefs(id),
    generated_at TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE report_items (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL REFERENCES daily_reports(id) ON DELETE CASCADE,
    news_item_id UUID NOT NULL REFERENCES news_items(id),
    score INTEGER NOT NULL,
    position INTEGER NOT NULL
);

-- Indexes
CREATE INDEX idx_sources_user_status ON sources(user_id, status);
CREATE INDEX idx_sources_status ON sources(status);
CREATE INDEX idx_news_items_source_guid ON news_items(source_id, guid);
CREATE INDEX idx_news_items_source_published ON news_items(source_id, published_at DESC);
CREATE INDEX idx_news_items_status ON news_items(status);
CREATE INDEX idx_news_items_created ON news_items(created_at);
CREATE INDEX idx_day_briefs_user_status ON day_briefs(user_id, status);
CREATE INDEX idx_day_briefs_schedule ON day_briefs(status, schedule_time);
CREATE INDEX idx_daily_reports_daybrief ON daily_reports(day_brief_id, generated_at DESC);
CREATE INDEX idx_report_items_report ON report_items(report_id, position);
```
