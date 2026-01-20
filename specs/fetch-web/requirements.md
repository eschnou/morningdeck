# Fetch Web Content - Requirements

## 1. Introduction

This feature enhances news item processing by fetching full article content from the web when only a snippet is available. RSS feeds and email newsletters often provide truncated content. By retrieving the full article from the linked URL before AI enrichment, we provide the LLM with complete context for better summarization and scoring.

## 2. Alignment with Product Vision

From `specs/product.md`, the Content Processing pipeline specifies:
- **Parse & Extract** — Clean HTML, extract main content, images, metadata
- **Summarize** — Generate concise summary (1-2 paragraphs)

The current implementation only processes content provided in the feed/email. When sources provide snippets, the AI receives incomplete information, resulting in:
- Lower quality summaries that miss key details
- Inaccurate relevance scoring
- Missing entity extraction

This feature directly supports the vision of "an intelligent system that reads for you" by ensuring full article access.

## 3. Requirements

### REQ-1: Fetch Web Content for News Items

**User Story:** As a system, I want to fetch full article content from the web when a news item has a valid URL, so that the AI enrichment receives complete content.

**Acceptance Criteria:**
- During news item processing (in `ProcessingWorker`), before calling the AI service, check if the item has a `link` field with a valid HTTP/HTTPS URL
- If URL exists, fetch the web page content
- Extract the main article content from the HTML (remove navigation, ads, sidebars)
- Convert extracted HTML to markdown using existing `HtmlToMarkdownConverter`
- Pass both original content (`cleanContent`) and fetched web content to the LLM

### REQ-2: Graceful Degradation

**User Story:** As a system, I want to continue processing news items when web fetch fails, so that temporary network issues don't block the entire pipeline.

**Acceptance Criteria:**
- If web fetch fails (timeout, 4xx/5xx errors, network issues), log the error and proceed with original content only
- Do not mark the news item as ERROR due to web fetch failure alone
- Set a reasonable timeout for web requests (10-15 seconds)
- Skip web fetch for non-HTTP URLs (e.g., `mailto:` links from email extraction)

### REQ-3: Update LLM Prompt

**User Story:** As a system, I want the LLM to receive structured input distinguishing original snippet from fetched article, so that it can leverage both appropriately.

**Acceptance Criteria:**
- Update `enrich-with-score.st` prompt to accept both `content` (original) and `webContent` (fetched)
- Instruct the LLM to prefer web content when available, use original as context
- If web content is empty/unavailable, the prompt should handle gracefully

### REQ-4: Content Extraction

**User Story:** As a system, I want to extract only the article body from web pages, so that the LLM receives clean, relevant content without boilerplate.

**Acceptance Criteria:**
- Extract main article content using readability algorithms (e.g., Jsoup with heuristics, or a library like Readability4J)
- Remove: navigation, headers, footers, sidebars, ads, comments sections
- Preserve: article title, body text, relevant images (as markdown)
- Handle common paywall indicators gracefully (log warning, proceed with original content)

### REQ-5: Store Fetched Content

**User Story:** As a system, I want to store the fetched web content, so that it can be referenced later without re-fetching.

**Acceptance Criteria:**
- Add a new field `webContent` (TEXT) to the `NewsItem` entity
- Store the markdown-converted web content in this field
- The field is nullable (empty if fetch failed or was skipped)

### REQ-6: Conditional Fetching

**User Story:** As a system, I want to skip web fetching when full content is already available, so that I don't waste resources on unnecessary requests.

**Acceptance Criteria:**
- Skip web fetch if `cleanContent` length exceeds a threshold (e.g., 2000 characters), indicating the source already provides full content
- Skip web fetch if `link` matches the source URL (common for sources that embed full content)
- Configuration option to disable web fetching globally or per-source (future consideration)

### REQ-7: Display Best Available Content in UI

**User Story:** As a user, I want to see the full article content when viewing a news item, so that I can read the complete article without leaving the application.

**Acceptance Criteria:**
- When returning news item data to the frontend, use fetched web content (`webContent`) if available
- Fall back to original content (`cleanContent`) when web content is not available
- Content selection logic is handled in the backend DTO mapping
- Frontend receives a single `content` field and requires no changes
- User sees improved content quality transparently

## 4. Non-Functional Requirements

### Architecture
- Implement web fetching as a separate provider component (`WebContentFetcher`) following the existing provider pattern
- Use the existing `HttpClient` configuration pattern from `RssFetcher`
- Consider rate limiting to avoid overwhelming target sites

### Performance
- Web fetch must have a hard timeout (configurable, default 15 seconds)
- Processing should not be significantly delayed; consider async fetch with fallback
- LLM input size limit: truncate combined content if it exceeds model limits (currently 4000 chars)

### Reliability
- Implement retry logic with exponential backoff (max 2 retries)
- Log all fetch attempts with source ID and URL for debugging
- Metrics: track success rate, average fetch time, common failure reasons

### Security
- Validate URLs before fetching (prevent SSRF attacks)
- Only allow HTTP/HTTPS protocols
- Do not follow more than 3 redirects
- Sanitize extracted content before storing

### Usability
- No user-facing changes required
- Improved summary quality should be transparent to users
