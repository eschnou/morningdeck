# Core Engine - Implementation Tasks

## Phase 1: Foundation - Database & Entities

**Goal:** Establish data model and persistence layer for all core engine entities.

**Verification:** Run `mvn test` - all migrations apply, entities compile, H2 tests pass.

### Tasks

- [x] **1.1 Add dependencies to pom.xml**
  - Add Rome RSS library (`com.rometools:rome:2.1.0`)
  - Add Spring AI OpenAI starter (`org.springframework.ai:spring-ai-openai-spring-boot-starter`)
  - Add Spring AI BOM to dependency management
  - Add WireMock for testing
  - Unit test: Build compiles successfully ✓

- [x] **1.2 Create Flyway migration V4__Core_engine_init.sql**
  - Create `sources` table with unique constraint on (user_id, url)
  - Create `news_items` table with unique constraint on (source_id, guid), JSONB tags column
  - Create `day_briefs` table
  - Create `day_brief_sources` join table
  - Create `daily_reports` table
  - Create `report_items` table
  - Create all indexes from design doc
  - Unit test: Migration applies on H2 ✓

- [x] **1.3 Create enums**
  - `SourceType` (RSS)
  - `SourceStatus` (ACTIVE, PAUSED, ERROR, DELETED)
  - `NewsItemStatus` (PENDING, SUMMARIZED, PROCESSED, ERROR)
  - `BriefingFrequency` (DAILY, WEEKLY)
  - `DayBriefStatus` (ACTIVE, PAUSED, DELETED)
  - `ReportStatus` (PENDING, GENERATED, ERROR)

- [x] **1.4 Create Source entity**
  - Fields: id, userId, name, url, type, status, tags, lastFetchedAt, lastError, etag, lastModified, createdAt, updatedAt
  - Use `@Enumerated(EnumType.STRING)` for enums
  - `@PrePersist` and `@PreUpdate` for timestamps
  - `@Builder.Default` for status = ACTIVE
  - Unit test: Entity instantiation with builder ✓

- [x] **1.5 Create NewsItem entity**
  - Fields: id, source (ManyToOne), guid, title, link, author, publishedAt, rawContent, cleanContent, summary, tags (JSONB), status, errorMessage, retryCount, createdAt, updatedAt
  - Create `NewsItemTags` class for JSONB mapping
  - Unit test: Entity instantiation, relationship to Source ✓

- [x] **1.6 Create DayBrief entity**
  - Fields: id, userId, title, description, briefing, frequency, scheduleTime, timezone, status, lastExecutedAt, sources (ManyToMany), createdAt, updatedAt
  - `getSourceIds()` helper method
  - Unit test: Entity instantiation, ManyToMany relationship ✓

- [x] **1.7 Create DailyReport entity**
  - Fields: id, dayBrief (ManyToOne), generatedAt, status, items (OneToMany), createdAt
  - `@OrderBy("position ASC")` on items
  - Unit test: Entity instantiation, relationships ✓

- [x] **1.8 Create ReportItem entity**
  - Fields: id, report (ManyToOne), newsItem (ManyToOne), score, position
  - Unit test: Entity instantiation ✓

- [x] **1.9 Create repositories**
  - `SourceRepository` extends JpaRepository<Source, UUID>
    - `findByUserIdAndStatus(UUID userId, SourceStatus status, Pageable pageable): Page<Source>`
    - `findByStatus(SourceStatus status): List<Source>`
    - `existsByUserIdAndUrl(UUID userId, String url): boolean`
    - `countByStatus(SourceStatus status): long`
  - `NewsItemRepository` extends JpaRepository<NewsItem, UUID>
    - `existsBySourceIdAndGuid(UUID sourceId, String guid): boolean`
    - `findByStatusInOrderByCreatedAtAsc(List<NewsItemStatus> statuses, Pageable pageable): List<NewsItem>`
    - `findBySourceIdInAndStatusAndPublishedAtAfter(List<UUID> sourceIds, NewsItemStatus status, LocalDateTime after): List<NewsItem>`
    - `countBySourceId(UUID sourceId): long`
  - `DayBriefRepository` extends JpaRepository<DayBrief, UUID>
    - `findByUserIdAndStatus(UUID userId, DayBriefStatus status, Pageable pageable): Page<DayBrief>`
    - `findDueBriefings(DayBriefStatus status, LocalDateTime now): List<DayBrief>` (custom query)
  - `DailyReportRepository` extends JpaRepository<DailyReport, UUID>
    - `findByDayBriefIdOrderByGeneratedAtDesc(UUID dayBriefId, Pageable pageable): Page<DailyReport>`
  - `ReportItemRepository` extends JpaRepository<ReportItem, UUID>
  - Unit test: Repository method signature compilation ✓

---

## Phase 2: Source Management API

**Goal:** Complete CRUD API for RSS sources with validation.

**Verification:** Run `SourceIT` integration test - all source endpoints work correctly.

### Tasks

- [x] **2.1 Create custom exceptions**
  - `SourceValidationException` extends BadRequestException
  - `DuplicateSourceException` extends BadRequestException
  - `SourceFetchException` extends RuntimeException
  - `AiProcessingException` extends RuntimeException
  - Update `GlobalExceptionHandler` with handlers for new exceptions
  - Unit test: Exception instantiation ✓

- [x] **2.2 Create UrlValidator utility**
  - Validate protocol (http/https only)
  - Block localhost, 127.0.0.1, 0.0.0.0, 169.254.169.254
  - Block private IP ranges (configurable for tests)
  - Throw `SourceValidationException` on failure
  - Unit test: `UrlValidatorTest` - valid URLs pass, blocked URLs fail ✓

- [x] **2.3 Create SourceFetcher interface and models**
  - `SourceFetcher` interface with `getSourceType()`, `validate()`, `fetch()` methods
  - `SourceValidationResult` (valid, feedTitle, feedDescription, errorMessage)
  - `FetchedItem` (guid, title, link, author, publishedAt, rawContent, cleanContent)
  - Unit test: Interface compilation ✓

- [x] **2.4 Implement RssFetcher**
  - Implement `SourceFetcher` for `SourceType.RSS`
  - Use Rome library to parse RSS/Atom feeds
  - `validate()`: Fetch feed, extract title/description, return result
  - `fetch()`: Fetch feed, parse items, strip HTML from content, return FetchedItem list
  - Handle HTTP caching (ETag, Last-Modified headers)
  - Unit test: `RssFetcherTest` - mock HTTP responses, verify parsing ✓

- [x] **2.5 Create SourceDTO**
  - Fields: id, url, name, type, status, tags, lastFetchedAt, lastError, itemCount, createdAt
  - `@NotBlank` on url
  - `@JsonInclude(NON_NULL)`
  - Unit test: DTO validation ✓

- [x] **2.6 Create SourceService**
  - Constructor inject: SourceRepository, List<SourceFetcher>, UrlValidator
  - `createSource(userId, url, name, type, tags)`: validate URL, check duplicate, fetch metadata, create entity
  - `getSource(userId, sourceId)`: ownership check, return entity
  - `listSources(userId, status, pageable)`: return Page<Source>
  - `updateSource(userId, sourceId, name, tags, status)`: ownership check, update fields
  - `deleteSource(userId, sourceId)`: ownership check, set status=DELETED
  - `validateSource(url, type)`: find fetcher, validate URL
  - Private `mapToDTO(Source, itemCount)` method
  - Unit test: `SourceServiceTest` - mock repository, test all methods ✓

- [x] **2.7 Create SourceController**
  - `POST /api/v1/sources` - create source
  - `GET /api/v1/sources` - list sources (status filter, pagination)
  - `GET /api/v1/sources/{id}` - get source
  - `PUT /api/v1/sources/{id}` - update source
  - `DELETE /api/v1/sources/{id}` - delete source (soft)
  - Use `@AuthenticationPrincipal` to get userId
  - Unit test: Controller method signatures ✓

- [x] **2.8 Create SourceIT integration test**
  - Setup: Create test user with auth token
  - Test create source with valid RSS URL (use WireMock)
  - Test create source with duplicate URL (409)
  - Test create source with invalid URL (400)
  - Test list sources with pagination
  - Test list sources with status filter
  - Test get source by id
  - Test get source with wrong user (404)
  - Test update source
  - Test delete source (soft delete) ✓

---

## Phase 3: Feed Ingestion Engine

**Goal:** Background job that fetches RSS feeds and creates NewsItem entities.

**Verification:** Start app, add a source, wait for ingestion, verify news_items in database.

### Tasks

- [x] **3.1 Add job configuration properties**
  - `application.jobs.feed-ingestion.enabled: true`
  - `application.jobs.feed-ingestion.interval: 900000` (15 min)
  - `application.jobs.news-processing.enabled: true`
  - `application.jobs.news-processing.interval: 60000` (1 min)
  - `application.jobs.briefing-execution.enabled: true`
  - `application.ai.provider=openai` ✓

- [x] **3.2 Create FeedIngestionJob**
  - `@Scheduled(fixedRateString = "${application.jobs.feed-ingestion.interval:900000}")`
  - Enabled property check inside method for testability
  - Fetch all ACTIVE sources
  - For each source: call fetcher, save new items, update lastFetchedAt
  - On error: set source status=ERROR, save lastError
  - Use `@Transactional` for each source fetch
  - Structured logging with source_id ✓

- [x] **3.3 Create mapToNewsItem helper**
  - Convert FetchedItem to NewsItem entity
  - Set status = PENDING
  - Link to Source ✓

- [x] **3.4 Create FeedIngestionIT integration test**
  - Setup: WireMock RSS server, create test user and source
  - Trigger job manually
  - Verify news items created in database
  - Verify source lastFetchedAt updated
  - Test error handling (WireMock returns 500)
  - Verify duplicate items not created on re-fetch ✓

---

## Phase 4: AI Processing Pipeline

**Goal:** Background job that processes news items through AI (summarize, tag).

**Verification:** Create news items manually, run processing, verify summary and tags populated.

### Tasks

- [x] **4.1 Create AI output records**
  - `SummaryResult` record with `@JsonPropertyOrder`
  - `TagsResult` record with nested EntitiesResult
  - `EntitiesResult` record (people, companies, technologies)
  - `ScoreResult` record (score, reasoning) ✓

- [x] **4.2 Create prompt resource files**
  - `src/main/resources/prompts/summarize.st`
  - `src/main/resources/prompts/extract-tags.st`
  - `src/main/resources/prompts/score-relevance.st` ✓

- [x] **4.3 Create AiService interface**
  - `summarize(title, content): SummaryResult`
  - `extractTags(title, content, summary): TagsResult`
  - `score(title, summary, briefingCriteria): ScoreResult` ✓

- [x] **4.4 Implement SpringAiService**
  - `@ConditionalOnProperty(prefix = "application.ai", name = "provider", havingValue = "openai")`
  - Inject ChatClient.Builder, build ChatClient
  - Load prompts from resources using `@Value("classpath:prompts/...")`
  - Implement all three methods using `chatClient.prompt().user().call().entity()`
  - `loadPrompt()` helper method
  - `truncate()` helper method ✓

- [x] **4.5 Create MockAiService for testing**
  - `@ConditionalOnProperty(prefix = "application.ai", name = "provider", havingValue = "mock", matchIfMissing = true)`
  - Return deterministic results for testing ✓

- [x] **4.6 Create NewsProcessingJob**
  - `@Scheduled(fixedRateString = "${application.jobs.news-processing.interval:60000}")`
  - Enabled property check inside method for testability
  - Fetch batch of PENDING or SUMMARIZED items
  - For PENDING: call summarize, set summary, status=SUMMARIZED
  - For SUMMARIZED: call extractTags, set tags, status=PROCESSED
  - Error handling: increment retryCount, set ERROR after 3 attempts ✓

- [x] **4.7 Create NewsProcessingIT integration test**
  - Setup: Create test source and news items with status=PENDING
  - Trigger job manually
  - Verify items progress through SUMMARIZED to PROCESSED
  - Verify summary and tags populated
  - Test retry logic with failing AI service ✓

---

## Phase 5: News Item API

**Goal:** API endpoints for viewing and searching news items.

**Verification:** Run `NewsItemIT` integration test - all news endpoints work correctly.

### Tasks

- [x] **5.1 Create NewsItemDTO**
  - Fields: id, title, link, author, publishedAt, content, summary, tags, sourceId, sourceName, createdAt
  - `@JsonInclude(NON_NULL)` ✓

- [x] **5.2 Create NewsItemService**
  - `getNewsItem(userId, newsItemId)`: ownership check via source.userId, return entity
  - `searchNewsItems(userId, query, sourceId, from, to, pageable)`: LIKE-based search, filter by user's sources
  - Private `mapToDTO(NewsItem)` method ✓

- [x] **5.3 Create NewsController**
  - `GET /api/v1/news` - search/list news items
    - Query params: q, sourceId, from, to, page, size
  - `GET /api/v1/news/{id}` - get news item detail ✓

- [x] **5.4 Add search to repository**
  - Custom `@Query` with LIKE-based search (H2 compatible)
  - `searchByQuery` and `findBySourceIdInAndFilters` methods ✓

- [x] **5.5 Create NewsItemIT integration test**
  - Setup: Create test user, source, and news items
  - Test get news item by id
  - Test get news item from other user's source (404)
  - Test search with query string
  - Test filter by sourceId
  - Test filter by date range
  - Test pagination ✓

---

## Phase 6: DayBrief Configuration API

**Goal:** CRUD API for configuring DayBriefs with source linking.

**Verification:** Run `DayBriefIT` integration test - all daybrief endpoints work correctly.

### Tasks

- [x] **6.1 Create DayBriefDTO**
  - Fields: id, title, description, briefing, sourceIds, frequency, scheduleTime, timezone, status, lastExecutedAt, sourceCount, createdAt
  - Validation: `@NotBlank` on title, briefing; `@NotEmpty` on sourceIds; `@NotNull` on frequency, scheduleTime ✓

- [x] **6.2 Create DayBriefService**
  - `createDayBrief(userId, title, description, briefing, sourceIds, frequency, scheduleTime, timezone)`: validate sources belong to user, create entity with ManyToMany relationship
  - `getDayBrief(userId, dayBriefId)`: ownership check
  - `listDayBriefs(userId, status, pageable)`: return Page<DayBrief>
  - `updateDayBrief(userId, dayBriefId, ...)`: ownership check, update fields and sources
  - `deleteDayBrief(userId, dayBriefId)`: ownership check, set status=DELETED
  - Private `mapToDTO(DayBrief)` method ✓

- [x] **6.3 Create DayBriefController**
  - `POST /api/v1/daybriefs` - create daybrief
  - `GET /api/v1/daybriefs` - list daybriefs
  - `GET /api/v1/daybriefs/{id}` - get daybrief
  - `PUT /api/v1/daybriefs/{id}` - update daybrief
  - `DELETE /api/v1/daybriefs/{id}` - delete daybrief ✓

- [x] **6.4 Create DayBriefIT integration test**
  - Setup: Create test user, sources
  - Test create daybrief with valid sources
  - Test create daybrief with other user's source (400)
  - Test create daybrief with missing fields (400 validation)
  - Test list daybriefs
  - Test get daybrief
  - Test update daybrief sources
  - Test delete daybrief ✓

---

## Phase 7: Briefing Execution & Reports

**Goal:** Execute briefings on schedule and generate reports with scored items.

**Verification:** Create daybrief, trigger execution, verify report created with scored items.

### Tasks

- [x] **7.1 Create DailyReportDTO and ReportItemDTO**
  - `DailyReportDTO`: id, dayBriefId, dayBriefTitle, dayBriefDescription, generatedAt, status, items, itemCount
  - `ReportItemDTO`: newsItemId, title, summary, link, publishedAt, score, position, sourceName
  - `@JsonInclude(NON_NULL)` ✓

- [x] **7.2 Create ReportService**
  - `getReport(userId, dayBriefId, reportId)`: ownership check via dayBrief.userId
  - `listReports(userId, dayBriefId, pageable)`: ownership check, return Page<DailyReport>
  - Private `mapToDTO(DailyReport)` method ✓

- [x] **7.3 Create BriefingExecutionJob**
  - `@Scheduled(cron = "0 * * * * *")` - every minute
  - Query for due briefings based on scheduleTime and timezone
  - For each due briefing: call `executeBriefing()`
  - `executeBriefing(DayBrief)`:
    1. Get processed news items from linked sources since lastExecutedAt
    2. Score each item against briefing criteria using AiService
    3. Sort by score, take top 10
    4. Create DailyReport and ReportItems
    5. Update lastExecutedAt
  - Handle errors gracefully, don't fail entire batch ✓

- [x] **7.4 Create findDueBriefings repository query**
  - Custom JPQL query considering frequency, scheduleTime, timezone, lastExecutedAt
  - Handle DAILY: due if current time >= scheduleTime and not executed today ✓

- [x] **7.5 Add report endpoints to DayBriefController**
  - `GET /api/v1/daybriefs/{id}/reports` - list reports for daybrief
  - `GET /api/v1/daybriefs/{id}/reports/{reportId}` - get specific report
  - `POST /api/v1/daybriefs/{id}/execute` - manually trigger execution ✓

- [x] **7.6 Create ReportIT integration test**
  - Setup: Create user, source, news items (PROCESSED), daybrief
  - Trigger manual execution via API
  - Verify report created with items
  - Verify items ordered by score
  - Test list reports
  - Test get report detail
  - Test report from other user's daybrief (404) ✓

---

## Phase 8: Polish & Production Readiness

**Goal:** Add monitoring, health checks, and configuration for production deployment.

**Verification:** Run full test suite, verify health endpoints, check metrics exposed.

### Tasks

- [x] **8.1 Add health indicators**
  - `FeedIngestionHealthIndicator`: check error source percentage ✓

- [x] **8.2 Add metrics**
  - `CoreEngineMetrics` component with MeterRegistry
  - `recordFeedFetch(sourceId, success, durationMs)`
  - `recordNewsProcessing(stage, success)`
  - `recordBriefingExecution(dayBriefId, itemCount)` ✓

- [x] **8.3 Add configuration properties class**
  - `@ConfigurationProperties(prefix = "application")`
  - Nested classes for `jobs`, `ai`
  - Validation with `@Validated` ✓

- [x] **8.4 Add Spring AI configuration**
  - `application.ai.provider` property
  - OpenAI API key from environment
  - Model configuration (gpt-4o) ✓

- [x] **8.5 Update application.yml for local profile**
  - Disable jobs for local development or set longer intervals
  - Use mock AI provider by default locally ✓

- [x] **8.6 Add test resources**
  - Sample RSS feed XML for WireMock (already exists in tests)
  - Test configuration overrides ✓

- [x] **8.7 Run full test suite and fix issues**
  - All 53 core-engine tests pass
  - 2 pre-existing avatar tests fail (unrelated to core-engine) ✓

- [ ] **8.8 Update API documentation**
  - Add OpenAPI annotations to new controllers
  - Verify Swagger UI shows new endpoints
