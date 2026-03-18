# Briefing-Core Refactoring Requirements

## Introduction

This feature refactors the core domain model to make **Briefing** the top-level entity. Sources become children of a Briefing, enabling relevance scoring to happen during news item processing (single LLM call) rather than at report generation time (separate scoring calls). This improves cost efficiency and provides immediate relevance scores when items are processed.

## Alignment with Product Vision

From `specs/product.md`:
- **"Reads for you"** — Sources are monitored per briefing context
- **"Thinks like you"** — Scoring against user-defined criteria happens inline during processing
- **"Briefs you"** — Daily reports become a filtered view of already-scored items
- **Content Processing Pipeline** (Section 5.2) lists: Fetch → Parse & Summarize → **Score & Tag** → Store — this refactoring aligns with that pipeline by combining enrichment and scoring

Current architecture separates:
1. Source fetching (independent of briefings)
2. Item enrichment (summary, tags — no scoring)
3. Report generation (scoring against briefing criteria)

Proposed architecture unifies:
1. Briefing-scoped source fetching
2. Item enrichment WITH scoring in single LLM call
3. Reports become filtered/sorted views of pre-scored items

## Requirements

### R1: Briefing as Aggregate Root

**User Story**: As a user, I want sources to belong to a specific briefing, so that news items are scored against my interests as soon as they arrive.

**Acceptance Criteria**:
- A Source belongs to exactly one Briefing (many-to-one relationship)
- Deleting a Briefing cascades to its Sources and NewsItems
- Existing many-to-many relationship (`day_brief_sources`) is removed
- Briefing retains: title, description, briefing criteria, frequency, scheduleTime, timezone, status
- Source retains: url, name, type, status, tags, fetch metadata
- Migration preserves existing data (sources attached to their first briefing, or a default briefing created)

### R2: Inline Scoring During Item Processing

**User Story**: As a user, I want each news item scored against my briefing criteria immediately during processing, so that relevance is available without additional LLM calls.

**Acceptance Criteria**:
- `ProcessingWorker` receives briefing context (criteria) when processing items
- `AiService.enrich()` extended to accept optional briefing criteria and return score
- NewsItem model extended with `score` (Integer, nullable) and `scoreReasoning` (String, nullable)
- If briefing criteria present: single LLM call returns summary + tags + score
- Score is 0-100 integer representing relevance to briefing criteria
- Items without briefing criteria (edge case) get null score

### R3: Briefing-Centric UI Navigation

**User Story**: As a user, I want to open a briefing and see the stream of news items from all its sources, so that I can browse content in context.

**Acceptance Criteria**:
- Briefing detail page shows aggregated news items from all briefing sources
- Items displayed in reverse chronological order by publishedAt
- Items can be filtered by:
  - Source (dropdown of briefing's sources)
  - Date range (today, yesterday, last 7 days, custom)
  - Read status (all, unread, read)
  - Saved status
  - Minimum score threshold (slider 0-100)
- Items can be sorted by:
  - Published date (default)
  - Score (highest first)
- Pagination with configurable page size

### R4: Daily Briefing View

**User Story**: As a user, I want to see a "briefing of the day" view that shows top-scored items for today, so that I get a curated summary.

**Acceptance Criteria**:
- Briefing page has "Today's Briefing" section/tab
- Shows items published in last 24h, sorted by score descending
- Configurable limit (default: top 10)
- Can navigate to previous days: "Yesterday", "2 days ago", etc.
- Each day's briefing is persisted as a DailyReport for historical reference
- DailyReport generation becomes a simple query (no LLM calls) of pre-scored items

### R5: Simplified Report Generation

**User Story**: As a user, I want daily reports generated from already-scored items, so that report generation is fast and deterministic.

**Acceptance Criteria**:
- `BriefingWorker` queries items by: briefing sources, status=DONE, publishedAt > lastExecutedAt, score not null
- Items sorted by score descending, limited to configured max (default 10)
- No additional LLM calls during report generation
- Report items reference NewsItem directly (existing ReportItem structure)
- Report generation completes in < 1 second for typical briefing

### R6: Source Management Within Briefing Context

**User Story**: As a user, I want to add/remove sources from within a briefing, so that source management is contextual.

**Acceptance Criteria**:
- Add Source action available from Briefing detail page
- Source form includes parent briefing (pre-selected, non-editable when adding from briefing context)
- Sources list view remains available (shows all sources across briefings)
- Source detail page shows parent briefing with link
- Moving a source between briefings re-queues its items for re-scoring (or marks scores as stale)

### R7: Data Migration
 Since we are still in dev we should't worry about data migration. 

## Non-Functional Requirements

### Architecture
- Source → Briefing relationship enforced at JPA level with proper cascade
- NewsItem processing queue scoped by briefing for rate limiting
- API endpoints reorganized: `/api/briefings/{id}/sources`, `/api/briefings/{id}/items`

### Performance
- News item list queries must return < 500ms for 1000 items
- Inline scoring adds < 2 seconds to item processing time
- Report generation < 1 second (query only, no LLM)

### Security
- Sources accessible only via their parent briefing's owner
- Cross-briefing item queries prevented at repository level

### Reliability
- Failed scoring should not block item enrichment (score = null, error logged)
- Stale scores flagged when briefing criteria changes

### Usability
- UI clearly indicates when items lack scores (e.g., "Score pending")
- Score displayed as visual indicator (color-coded badge or bar)
- Filter/sort controls persist across page navigation
