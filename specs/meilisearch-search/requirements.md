# Meilisearch Search - Requirements

## Overview

Implement instant "search-as-you-type" functionality for articles (NewsItems) using Meilisearch. The feature must be **optional** - the application should work identically when Meilisearch is not configured.

## Functional Requirements

### FR-1: Instant Search

- **FR-1.1**: Users can search articles with results updating as they type (typeahead)
- **FR-1.2**: Search should return results within 50ms for responsive UX
- **FR-1.3**: Search should support typo tolerance (e.g., "articel" finds "article")
- **FR-1.4**: Search should match against: title, summary, content, author, tags

### FR-2: Scoped Search

- **FR-2.1**: Search is scoped to a single DayBrief (user searches within their brief)
- **FR-2.2**: User can only search their own articles (strict tenant isolation)
- **FR-2.3**: No data leakage between users under any circumstances

### FR-3: Filter Integration

Search results must respect existing filter combinations:

- **FR-3.1**: Read/Unread status (`readAt` null = unread)
- **FR-3.2**: Saved status (`saved` boolean)
- **FR-3.3**: Minimum score threshold (`score >= minScore`)
- **FR-3.4**: Source filter (specific source within brief)
- **FR-3.5**: Date range (publishedAt within range)

### FR-4: Optional Feature

- **FR-4.1**: Application functions normally without Meilisearch configured
- **FR-4.2**: Backend gracefully falls back to existing PostgreSQL search when Meilisearch unavailable
- **FR-4.3**: Frontend shows standard search UI regardless of backend implementation
- **FR-4.4**: Feature can be enabled/disabled via configuration

## Non-Functional Requirements

### NFR-1: Performance

- **NFR-1.1**: Search latency < 50ms (p95)
- **NFR-1.2**: Index sync should not block article creation
- **NFR-1.3**: Bulk re-indexing should not impact API performance

### NFR-2: Reliability

- **NFR-2.1**: Meilisearch unavailability must not crash the application
- **NFR-2.2**: Index can be rebuilt from PostgreSQL at any time
- **NFR-2.3**: Eventual consistency acceptable (sync delay < 5 seconds)

### NFR-3: Security

- **NFR-3.1**: User ID must be included in every document for tenant isolation
- **NFR-3.2**: All search queries must filter by user ID
- **NFR-3.3**: API keys must be stored securely (not in code)

### NFR-4: Operability

- **NFR-4.1**: Health check endpoint should include Meilisearch status
- **NFR-4.2**: Index statistics should be available for monitoring
- **NFR-4.3**: Re-index command available for admin operations

## User Stories

### US-1: Search Within Brief
> As a user, I want to search for articles within my brief so that I can quickly find relevant content without scrolling through pages.

### US-2: Instant Results
> As a user, I want to see search results update as I type so that I get immediate feedback and can refine my query.

### US-3: Filtered Search
> As a user, I want my search to respect my current filters (unread only, saved, top-scored) so that I can narrow down results efficiently.

### US-4: Typo Tolerance
> As a user, I want the search to understand my intent even with typos so that minor mistakes don't prevent me from finding content.

## Out of Scope (v1)

- Global search across all briefs
- Semantic/vector search (future enhancement with pg_vector)
- Search suggestions/autocomplete dropdown
- Search analytics/popular queries
- Saved searches
