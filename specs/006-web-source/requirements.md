# Web Source Feature Requirements

## Introduction

Web Source is a new source type that scrapes web pages and uses LLM extraction to identify news items. Unlike RSS which relies on structured feed formats, Web Source fetches raw HTML and applies a user-defined prompt to extract news items with title, content, and link.

Primary use case: Monitor pages without RSS feeds (e.g., Hacker News front page, product blogs, news aggregators) and extract relevant items based on custom criteria.

## Alignment with Product Vision

From `specs/product.md` Section 5.1 (Source Management):

> **Website Monitoring** | Scrape specific pages on schedule | URL + custom prompt (e.g., "Extract top 30 posts from Hacker News front page")

This feature directly implements the Website Monitoring source type, completing the ingestion layer alongside RSS and Email sources.

## Requirements

### R1: Create Web Source

**User Story:** As a user, I want to add a web source with a URL and extraction prompt, so that I can monitor pages without RSS feeds.

**Acceptance Criteria:**
- User provides: URL, name, extraction prompt, refresh interval
- System validates URL is reachable (HTTP 200) before creation
- Source is created with type `WEB`
- Source belongs to exactly one briefing (same as RSS/Email)
- Duplicate URL within same briefing is rejected
- Default refresh interval: 1 hour (configurable in hours or days)

### R2: Fetch Web Content

**User Story:** As a user, I want the system to periodically fetch my web sources, so that new content is discovered automatically.

**Acceptance Criteria:**
- Fetcher retrieves HTML content from the URL
- HTML is converted to clean text/markdown for LLM processing
- Fetch respects robots.txt and includes appropriate User-Agent
- Fetch timeout: 30 seconds
- Failed fetches mark source status as ERROR with error message
- Successful fetch updates `lastFetchedAt` timestamp

### R3: LLM-Based Item Extraction

**User Story:** As a user, I want the system to use my extraction prompt to identify news items from the page, so that I get structured items matching my criteria.

**Acceptance Criteria:**
- LLM receives: page content (markdown), user's extraction prompt
- LLM returns array of extracted items, each containing:
  - `title` (required): Item headline
  - `content` (required): Item description/summary
  - `link` (required): Absolute URL to the item
- Relative links are resolved against the source URL
- Maximum 50 items extracted per fetch
- Extraction failures create a single fallback item with ERROR status

### R4: Item Deduplication

**User Story:** As a user, I want duplicate items to be skipped, so that I don't see the same content repeatedly.

**Acceptance Criteria:**
- GUID is derived from the item's link (normalized URL)
- Items with existing GUID for this source are skipped
- Link normalization: lowercase hostname, remove trailing slash, remove common tracking parameters (utm_*, ref, etc.)

### R5: Source Management

**User Story:** As a user, I want to manage web sources like other source types, so that I have a consistent experience.

**Acceptance Criteria:**
- List/view/update/delete operations work identically to RSS/Email
- Editable fields: name, tags, status, refresh interval, extraction prompt
- URL is immutable after creation (delete and recreate if needed)
- Manual refresh triggers immediate fetch
- Source health dashboard shows fetch status and errors

## Non-Functional Requirements

### Architecture
- Implement `WebFetcher` following the `SourceFetcher` interface pattern
- Add `WEB` to `SourceType` enum
- Store extraction prompt in new `extractionPrompt` column on Source entity
- State transitions identical to RSS sources:
  - Source: ACTIVE/PAUSED/ERROR with fetch status IDLE/QUEUED/FETCHING
  - NewsItem: NEW → PENDING → PROCESSING → DONE/ERROR
- Reuse existing fetch queue, processing pipeline, and scheduling jobs

### Performance
- Web fetch timeout: 30 seconds
- LLM extraction timeout: 60 seconds
- Rate limit: Maximum 1 fetch per source per 15 minutes
- Memory: Truncate page content to 100KB before LLM processing

### Security
- Validate URL scheme is HTTP or HTTPS only
- Block requests to private IP ranges (localhost, 10.x, 192.168.x, etc.)
- Sanitize extracted content to prevent XSS in frontend
- User-Agent header identifies the service

### Reliability
- Retry failed fetches up to 3 times with exponential backoff
- Mark source as ERROR after 3 consecutive failures
- Log extraction prompts and results for debugging
- Handle malformed HTML gracefully (best-effort extraction)

### Usability
- Provide example extraction prompts in UI
- Show preview of extracted items during source creation (optional enhancement)
- Clear error messages for common failures (timeout, blocked, invalid HTML)
