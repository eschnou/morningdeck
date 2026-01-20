# Core Engine - Requirements

## 1. Introduction

The Core Engine is the foundational feature of daybrief.ai that implements the complete content pipeline: source management, content ingestion, AI-powered processing, briefing configuration, and report generation. This MVP focuses on RSS feed support and backend API implementation.

## 2. Alignment with Product Vision

This feature implements the core value proposition from product.md:
- **Source Management** (§5.1): RSS feed subscription and management
- **Content Processing** (§5.2): Fetch, parse, summarize, score, and tag pipeline
- **Daily Briefing** (§5.3): Configurable briefings with AI-filtered content delivery

The engine enables the "reads for you, thinks like you, briefs you" workflow for knowledge professionals.

## 3. Functional Requirements

### 3.1 Source Management

**US-3.1.1: Add RSS Source**
> As a user, I want to add an RSS feed URL as a source, so that news items from that feed are ingested into the system.

Acceptance Criteria:
- User provides: URL, name (optional, auto-detected from feed), category/tags (optional)
- System validates URL is a valid RSS/Atom feed
- System stores source with status: ACTIVE, PAUSED, ERROR
- Source is linked to the user who created it
- Duplicate URL detection per user

**US-3.1.2: List Sources**
> As a user, I want to view all my sources, so that I can manage my content feeds.

Acceptance Criteria:
- Returns list of sources with: id, name, url, type, status, lastFetchedAt, itemCount, createdAt
- Support filtering by status
- Support pagination

**US-3.1.3: Update Source**
> As a user, I want to update my source settings, so that I can change name, tags, or status.

Acceptance Criteria:
- User can update: name, tags, status (ACTIVE/PAUSED)
- Cannot change URL (must delete and recreate)

**US-3.1.4: Delete Source**
> As a user, I want to delete a source, so that it stops being fetched.

Acceptance Criteria:
- Soft delete: marks source as DELETED
- News items from source remain in system but are not included in future briefings

### 3.2 News Ingestion Engine

**US-3.2.1: Fetch RSS Feeds**
> As the system, I want to periodically fetch RSS feeds, so that new items are ingested.

Acceptance Criteria:
- Scheduler runs every 15 minutes (configurable)
- Fetches only ACTIVE sources
- Respects HTTP caching headers (ETag, Last-Modified)
- Rate limiting: max 1 request per source per 5 minutes
- Updates source.lastFetchedAt and source.lastError on completion

**US-3.2.2: Parse Feed Items**
> As the system, I want to parse RSS/Atom items into normalized NewsItem entities.

Acceptance Criteria:
- Extract: title, link, publishedAt, author, content/description, guid
- Deduplicate by guid per source
- Store raw content and cleaned content (HTML stripped)
- Set status: PENDING (awaiting processing)

### 3.3 News Processing Pipeline

**US-3.3.1: Summarize News Item**
> As the system, I want to generate a summary for each news item, so that users get concise content.

Acceptance Criteria:
- Generate 2-3 sentence summary using LLM
- Store summary alongside original content
- Mark item as SUMMARIZED after completion

**US-3.3.2: Tag News Item**
> As the system, I want to auto-categorize news items, so that they can be filtered and organized.

Acceptance Criteria:
- Extract: topics (list), entities (people, companies, technologies), sentiment (positive/neutral/negative)
- Store tags as JSON structure on NewsItem
- Mark item as TAGGED after completion

**US-3.3.3: Score News Item**
> As the system, I want to score news items against user criteria, so that relevance can be determined per briefing.

Acceptance Criteria:
- Scoring is per-briefing (same news item has different scores for different briefings)
- Score range: 0-100
- Score computed using LLM with user's briefing criteria as context
- Store score in BriefingNewsScore join entity

**US-3.3.4: Process Pipeline Orchestration**
> As the system, I want to process news items through the complete pipeline.

Acceptance Criteria:
- Pipeline: PENDING → SUMMARIZED → TAGGED → PROCESSED
- Failed items marked with status ERROR and error message
- Retry logic: max 3 attempts with exponential backoff
- Processing triggered by scheduler or on-demand

### 3.4 DayBrief Configuration

**US-3.4.1: Create DayBrief**
> As a user, I want to create a DayBrief, so that I can receive personalized briefings.

Acceptance Criteria:
- User provides: title, description, sourceIds (one or more)
- User provides: briefing (text describing goals/interests for AI filtering)
- User provides: frequency (DAILY, WEEKLY)
- User provides: scheduleTime (time of day for delivery, in user's timezone)
- DayBrief linked to user
- Default status: ACTIVE

**US-3.4.2: List DayBriefs**
> As a user, I want to view all my DayBriefs, so that I can manage my briefings.

Acceptance Criteria:
- Returns list with: id, title, description, frequency, scheduleTime, status, lastExecutedAt, sourceCount
- Support filtering by status

**US-3.4.3: Update DayBrief**
> As a user, I want to update my DayBrief settings.

Acceptance Criteria:
- User can update: title, description, sourceIds, briefing, frequency, scheduleTime, status

**US-3.4.4: Delete DayBrief**
> As a user, I want to delete a DayBrief, so that it stops executing.

Acceptance Criteria:
- Soft delete: marks DayBrief as DELETED
- Past reports remain accessible
- Can be restored within 30 days

### 3.5 Briefing Execution & Report Generation

**US-3.5.1: Execute Briefing**
> As the system, I want to execute a DayBrief on schedule, so that users receive their reports.

Acceptance Criteria:
- Scheduler checks for due briefings based on frequency and scheduleTime
- Collect news items from linked sources since last execution
- Filter items where publishedAt > lastExecutedAt
- Score each item against briefing criteria using LLM
- Select top N items (configurable, default 10) by score
- Create DailyReport with selected items
- Update DayBrief.lastExecutedAt

**US-3.5.2: Generate Daily Report**
> As the system, I want to generate a DailyReport, so that users can view their briefing results.

Acceptance Criteria:
- DailyReport contains: id, dayBriefId, generatedAt, items (ordered by score)
- Each item includes: newsItemId, score, title, summary, link, publishedAt
- Report status: PENDING → GENERATED
- Store report for user access

**US-3.5.3: View Daily Report**
> As a user, I want to view a generated report, so that I can read my briefing.

Acceptance Criteria:
- Returns full report with all items and metadata
- Includes DayBrief title and description for context

**US-3.5.4: List Daily Reports**
> As a user, I want to view past reports for a DayBrief, so that I can access historical briefings.

Acceptance Criteria:
- Returns paginated list of reports for a DayBrief
- Ordered by generatedAt descending
- Include: id, generatedAt, itemCount

### 3.6 News Item Access

**US-3.6.1: View News Item**
> As a user, I want to view a full news item, so that I can read the complete content.

Acceptance Criteria:
- Returns: id, title, link, publishedAt, author, content, summary, tags, sourceId
- Only accessible if news item belongs to one of user's sources

**US-3.6.2: Search News Items**
> As a user, I want to search through ingested news items, so that I can find specific content.

Acceptance Criteria:
- Full-text search on title and content
- Filter by: sourceId, dateRange, tags
- Returns paginated results with: id, title, summary, publishedAt, sourceName
- Results limited to user's sources

## 4. Non-Functional Requirements

### 4.1 Architecture

- Follow existing package structure: model, repository, service, controller, dto
- Services must be channel-agnostic (no DTOs, only entities)
- Use JPA relationships for entity associations
- New entities: Source, NewsItem, DayBrief, DailyReport, BriefingNewsScore
- Have a proper abstraction so we can easily additional type of sources
- Processing jobs use Spring's @Scheduled or @Async with configurable thread pool
- LLM integration via abstracted provider interface (similar to existing StorageProvider pattern)

### 4.2 Performance

- RSS fetch timeout: 30 seconds max
- Database indexes on where needed
- Pagination default: 20 items, max 100

### 4.3 Security

- All endpoints require authentication (JWT)
- Users can only access their own sources, dayBriefs, and reports
- Source URLs validated to prevent SSRF (whitelist protocols: http, https)
- Rate limit source creation: 50 per user

### 4.4 Reliability

- Idempotent news item creation (dedupe by guid)
- Failed processing items retried with exponential backoff
- Source fetch errors logged and surfaced in source status
- Scheduler resilient to individual job failures

### 4.5 Data Model Summary

```
Source
- id: UUID
- userId: UUID (FK)
- name: String
- url: String
- type: Enum (RSS)
- status: Enum (ACTIVE, PAUSED, ERROR, DELETED)
- tags: String[]
- lastFetchedAt: DateTime
- lastError: String
- createdAt: DateTime
- updatedAt: DateTime

NewsItem
- id: UUID
- sourceId: UUID (FK)
- guid: String (unique per source)
- title: String
- link: String
- author: String
- publishedAt: DateTime
- rawContent: Text
- cleanContent: Text
- summary: Text
- tags: JSON (topics, entities, sentiment)
- status: Enum (PENDING, SUMMARIZED, TAGGED, PROCESSED, ERROR)
- errorMessage: String
- createdAt: DateTime
- updatedAt: DateTime

DayBrief
- id: UUID
- userId: UUID (FK)
- title: String
- description: String
- briefing: Text (user's goals/interests)
- frequency: Enum (DAILY, WEEKLY)
- scheduleTime: Time
- timezone: String
- status: Enum (ACTIVE, PAUSED, DELETED)
- lastExecutedAt: DateTime
- createdAt: DateTime
- updatedAt: DateTime

DayBriefSource (join table)
- dayBriefId: UUID (FK)
- sourceId: UUID (FK)

DailyReport
- id: UUID
- dayBriefId: UUID (FK)
- generatedAt: DateTime
- status: Enum (PENDING, GENERATED, ERROR)
- createdAt: DateTime

ReportItem
- id: UUID
- reportId: UUID (FK)
- newsItemId: UUID (FK)
- score: Integer (0-100)
- position: Integer (ordering)
```

### 4.6 API Endpoints Summary

```
Sources:
POST   /api/v1/sources           - Create source
GET    /api/v1/sources           - List sources
GET    /api/v1/sources/{id}      - Get source
PUT    /api/v1/sources/{id}      - Update source
DELETE /api/v1/sources/{id}      - Delete source

News Items:
GET    /api/v1/news              - Search/list news items
GET    /api/v1/news/{id}         - Get news item

DayBriefs:
POST   /api/v1/daybriefs         - Create daybrief
GET    /api/v1/daybriefs         - List daybriefs
GET    /api/v1/daybriefs/{id}    - Get daybrief
PUT    /api/v1/daybriefs/{id}    - Update daybrief
DELETE /api/v1/daybriefs/{id}    - Delete daybrief

Reports:
GET    /api/v1/daybriefs/{id}/reports      - List reports for daybrief
GET    /api/v1/daybriefs/{id}/reports/{reportId} - Get specific report
POST   /api/v1/daybriefs/{id}/execute      - Manually trigger briefing execution
```
