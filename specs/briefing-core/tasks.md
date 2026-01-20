# Briefing-Core Implementation Tasks

## Phase 1: Domain Model Refactoring

**Goal**: Change Source → DayBrief relationship from many-to-many to many-to-one.

**Verification**: Integration tests pass for Source and DayBrief CRUD with new relationship.

### Tasks

#### 1.1 Update Source Entity
- Remove `@ManyToMany(mappedBy = "sources")` and `Set<DayBrief> dayBriefs` field
- Add `@ManyToOne(fetch = FetchType.LAZY)` with `@JoinColumn(name = "day_brief_id", nullable = false)` field `DayBrief dayBrief`
- Update `@EqualsAndHashCode(exclude = {...})` to exclude `dayBrief` instead of `dayBriefs`
- Remove `newsItems` from exclude list (keep it)

**File**: `backend/src/main/java/be/transcode/daybrief/server/core/model/Source.java`

#### 1.2 Update DayBrief Entity
- Remove `@ManyToMany` with `@JoinTable` annotation
- Add `@OneToMany(mappedBy = "dayBrief", cascade = CascadeType.ALL, orphanRemoval = true)` for `List<Source> sources`
- Update `getSourceIds()` helper method to work with new structure
- Remove `userId` field (ownership now via sources)

**File**: `backend/src/main/java/be/transcode/daybrief/server/core/model/DayBrief.java`

#### 1.3 Add NewsItem Score Fields
- Add `Integer score` field (nullable)
- Add `String scoreReasoning` field (max 512 chars)

**File**: `backend/src/main/java/be/transcode/daybrief/server/core/model/NewsItem.java`

#### 1.4 Create Flyway Migration
- Drop `day_brief_sources` join table
- Add `day_brief_id` column to `sources` table with foreign key
- Add `score` and `score_reasoning` columns to `news_items` table
- Create index on `news_items(score)`

**File**: `backend/src/main/resources/db/migrations/V10__briefing_core_refactor.sql`

#### 1.5 Update SourceIT Tests
- Update `createTestSource()` helper to require a DayBrief
- Update all test setup to create DayBrief before creating Sources
- Add test: create source without briefing should fail

**File**: `backend/src/test/java/be/transcode/daybrief/server/core/controller/SourceIT.java`

#### 1.6 Update DayBriefIT Tests
- Update tests to reflect cascade behavior
- Add test: delete DayBrief should cascade delete its Sources
- Add test: verify sources are returned with briefing

**File**: `backend/src/test/java/be/transcode/daybrief/server/core/controller/DayBriefIT.java`

---

## Phase 2: Service Layer Updates

**Goal**: Update services to work with new relationship and add briefing-scoped source creation.

**Verification**: All existing integration tests pass + new tests for briefing-scoped operations.

### Tasks

#### 2.1 Update SourceService
- Add `UUID briefingId` parameter to `createSource()`
- Validate briefing exists and belongs to user
- Set `source.setDayBrief(dayBrief)` instead of userId
- Update `getSourceWithOwnershipCheck()` to check via `source.getDayBrief().getUserId()`
- Remove join table cleanup from `deleteSource()`

**File**: `backend/src/main/java/be/transcode/daybrief/server/core/service/SourceService.java`

#### 2.2 Update SourceDTO
- Add `UUID briefingId` field
- Add `String briefingTitle` field (for display)
- Update mapToDTO to populate new fields

**File**: `backend/src/main/java/be/transcode/daybrief/server/core/dto/SourceDTO.java`

#### 2.3 Update DayBriefService
- Remove `validateAndGetSources()` method (no longer selecting existing sources)
- Update `createDayBrief()` to not accept sourceIds (sources created separately)
- Update `deleteDayBrief()` - cascade handles source deletion automatically
- Keep `updateDayBrief()` but remove sourceIds parameter

**File**: `backend/src/main/java/be/transcode/daybrief/server/core/service/DayBriefService.java`

#### 2.4 Update DayBriefDTO
- Remove `sourceIds` field from request (sources created via separate endpoint)
- Keep `sourceIds` in response (computed from sources)
- Update builders/constructors

**File**: `backend/src/main/java/be/transcode/daybrief/server/core/dto/DayBriefDTO.java`

#### 2.5 Update NewsItemService
- Update ownership check to use `source.getDayBrief().getUserId()`
- Update source lookup to go via briefing

**File**: `backend/src/main/java/be/transcode/daybrief/server/core/service/NewsItemService.java`

---

## Phase 3: API Endpoint Reorganization

**Goal**: Add briefing-scoped endpoints for sources and items.

**Verification**: New API endpoints work correctly with integration tests.

### Tasks

#### 3.1 Add Briefing-Scoped Source Endpoints
- `GET /daybriefs/{id}/sources` - list sources for briefing
- `POST /daybriefs/{id}/sources` - add source to briefing
- Keep existing `/sources/*` endpoints for backward compatibility

**File**: `backend/src/main/java/be/transcode/daybrief/server/core/controller/DayBriefController.java`

#### 3.2 Update SourceController
- Update `createSource()` to require `briefingId` in request body
- Delegate to updated `SourceService.createSource()`

**File**: `backend/src/main/java/be/transcode/daybrief/server/core/controller/SourceController.java`

#### 3.3 Add NewsItemRepository Queries
- Add `findByBriefingWithFilters()` - paginated items for briefing with filters
- Add `findTopScoredItems()` - top scored items for report generation

**File**: `backend/src/main/java/be/transcode/daybrief/server/core/repository/NewsItemRepository.java`

#### 3.4 Add Briefing Items Endpoint
- `GET /daybriefs/{id}/items` - list items with filters (sourceId, minScore, readStatus, saved, sortBy)
- Implement in DayBriefController using new repository method

**File**: `backend/src/main/java/be/transcode/daybrief/server/core/controller/DayBriefController.java`

#### 3.5 Integration Test: Briefing Sources API
- Test `GET /daybriefs/{id}/sources` returns only that briefing's sources
- Test `POST /daybriefs/{id}/sources` creates source attached to briefing
- Test authorization: cannot access other user's briefing sources

**File**: `backend/src/test/java/be/transcode/daybrief/server/core/controller/BriefingSourcesIT.java` (new)

#### 3.6 Integration Test: Briefing Items API
- Test `GET /daybriefs/{id}/items` returns aggregated items
- Test filters: sourceId, minScore, readStatus, saved
- Test sorting by publishedAt and score

**File**: `backend/src/test/java/be/transcode/daybrief/server/core/controller/BriefingItemsIT.java` (new)

---

## Phase 4: Inline Scoring

**Goal**: Combine enrichment and scoring in single LLM call during item processing.

**Verification**: ProcessingWorkerIT passes with score populated on processed items.

### Tasks

#### 4.1 Create EnrichmentWithScoreResult Record
- Fields: summary, topics, entities, sentiment, score (Integer), scoreReasoning (String)

**File**: `backend/src/main/java/be/transcode/daybrief/server/provider/ai/model/EnrichmentWithScoreResult.java`

#### 4.2 Update AiService Interface
- Add `EnrichmentWithScoreResult enrichWithScore(String title, String content, String briefingCriteria)`
- Keep existing `enrich()` method for backward compatibility

**File**: `backend/src/main/java/be/transcode/daybrief/server/provider/ai/AiService.java`

#### 4.3 Create Combined Prompt
- Create `enrich-with-score.st` combining enrichment and scoring instructions
- Include briefingCriteria placeholder

**File**: `backend/src/main/resources/prompts/enrich-with-score.st`

#### 4.4 Update SpringAiService
- Implement `enrichWithScore()` using new prompt
- Load and use `enrich-with-score.st` template

**File**: `backend/src/main/java/be/transcode/daybrief/server/provider/ai/SpringAiService.java`

#### 4.5 Update MockAiService
- Implement `enrichWithScore()` returning mock score (e.g., 75)
- Return mock reasoning

**File**: `backend/src/main/java/be/transcode/daybrief/server/provider/ai/MockAiService.java`

#### 4.6 Update ProcessingWorker
- Get briefing criteria from `item.getSource().getDayBrief().getBriefing()`
- Call `aiService.enrichWithScore()` instead of `aiService.enrich()`
- Set `item.setScore()` and `item.setScoreReasoning()`
- Handle scoring failure gracefully (score = null, continue with enrichment)

**File**: `backend/src/main/java/be/transcode/daybrief/server/core/queue/ProcessingWorker.java`

#### 4.7 Update ProcessingWorkerIT
- Update test setup to create DayBrief for Source
- Add test: processed item should have score populated
- Add test: score should match briefing criteria relevance

**File**: `backend/src/test/java/be/transcode/daybrief/server/core/queue/ProcessingWorkerIT.java`

---

## Phase 5: Simplified Report Generation

**Goal**: BriefingWorker generates reports from pre-scored items without LLM calls.

**Verification**: ReportIT passes with reports generated from pre-scored items.

### Tasks

#### 5.1 Update BriefingWorker
- Remove `scoreItem()` method and LLM scoring loop
- Query items using `findTopScoredItems()` (already scored)
- Filter by: status=DONE, publishedAt > lastExecutedAt, score NOT NULL
- Sort by score DESC, limit to MAX_REPORT_ITEMS

**File**: `backend/src/main/java/be/transcode/daybrief/server/core/queue/BriefingWorker.java`

#### 5.2 Update NewsItemDTO
- Add `Integer score` field
- Add `String scoreReasoning` field

**File**: `backend/src/main/java/be/transcode/daybrief/server/core/dto/NewsItemDTO.java`

#### 5.3 Integration Test: Report Generation
- Create briefing with sources
- Create pre-scored news items
- Execute briefing
- Verify report contains top-scored items

**File**: `backend/src/test/java/be/transcode/daybrief/server/core/controller/ReportIT.java`

---

## Phase 6: Frontend - Types and API Client

**Goal**: Update frontend types and API client for new backend structure.

**Verification**: TypeScript compiles without errors.

### Tasks

#### 6.1 Update Types
- Add `briefingId` and `briefingTitle` to `SourceDTO`
- Add `score` and `scoreReasoning` to `NewsItemDTO`
- Update `CreateSourceRequest` to include `briefingId`
- Remove `sourceIds` from `CreateDayBriefRequest`

**File**: `frontend/src/types/index.ts`

#### 6.2 Update API Client
- Add `getBriefingSources(briefingId, page, size)`
- Add `createBriefingSource(briefingId, data)`
- Add `getBriefingItems(briefingId, page, size, filters)`
- Update `createSource()` to require briefingId

**File**: `frontend/src/lib/api.ts`

---

## Phase 7: Frontend - Briefing Detail Page Redesign

**Goal**: Redesign BriefDetailPage with tabs for items, sources, and reports.

**Verification**: User can view items, sources, and reports within briefing context.

### Tasks

#### 7.1 Create ScoreBadge Component
- Display score as colored badge (green >= 70, yellow >= 40, gray < 40)
- Show "Pending" for null scores

**File**: `frontend/src/components/shared/ScoreBadge.tsx`

#### 7.2 Create BriefingItemsList Component
- Fetch items via `getBriefingItems()`
- Display items with score badges
- Filter controls: source dropdown, score slider, read/saved toggles
- Sort by: publishedAt, score

**File**: `frontend/src/components/briefs/BriefingItemsList.tsx`

#### 7.3 Create BriefingSourcesList Component
- Fetch sources via `getBriefingSources()`
- Display sources with item counts
- Add Source button triggers AddSourceDialog with briefingId

**File**: `frontend/src/components/briefs/BriefingSourcesList.tsx`

#### 7.4 Redesign BriefDetailPage
- Add tabs: Items, Sources, Reports
- Items tab: BriefingItemsList
- Sources tab: BriefingSourcesList with Add Source
- Reports tab: existing reports list
- Keep header with brief info and actions

**File**: `frontend/src/pages/BriefDetailPage.tsx`

#### 7.5 Update AddSourceDialog
- Accept optional `briefingId` prop
- When provided, pre-select briefing (disabled dropdown)
- When not provided, show briefing selector

**File**: `frontend/src/components/sources/AddSourceDialog.tsx`

#### 7.6 Update BriefForm
- Remove source selection (sources added via Sources tab now)
- Keep other fields: title, description, briefing criteria, frequency, schedule

**File**: `frontend/src/components/briefs/BriefForm.tsx`

---

## Phase 8: Frontend - Source Updates

**Goal**: Update SourcesPage and SourceDetailPage for new model.

**Verification**: User can view all sources and see parent briefing.

### Tasks

#### 8.1 Update SourceCard
- Display parent briefing name
- Link to parent briefing

**File**: `frontend/src/components/sources/SourceCard.tsx`

#### 8.2 Update SourceDetailPage
- Show parent briefing info with link
- Update source management to work within briefing context

**File**: `frontend/src/pages/SourceDetailPage.tsx`

#### 8.3 Update SourcesPage
- Keep as overview of all sources across briefings
- Show briefing name for each source
- Filter by briefing (optional)

**File**: `frontend/src/pages/SourcesPage.tsx`

---

## Phase 9: Cleanup and Polish

**Goal**: Remove deprecated code, fix edge cases, ensure consistency.

**Verification**: Full test suite passes, no console errors in UI.

### Tasks

#### 9.1 Remove Deprecated Code
- Remove old `score()` method from AiService if unused
- Remove `score-relevance.st` prompt if unused
- Clean up unused imports

#### 9.2 Update NewsItemRow
- Display score badge
- Show score reasoning on hover/click

**File**: `frontend/src/components/sources/NewsItemRow.tsx`

#### 9.3 End-to-End Verification
- Create briefing
- Add source to briefing
- Wait for source fetch and item processing
- Verify items have scores
- Execute briefing
- Verify report contains top-scored items
