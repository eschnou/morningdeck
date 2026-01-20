# Meilisearch Search - Implementation Tasks

## Phase 1: Infrastructure Setup

### Task 1.1: Add Meilisearch to Docker Compose
- [ ] Add meilisearch service to `docker-compose.yml`
- [ ] Configure volumes for data persistence
- [ ] Add health check
- [ ] Add to `.gitignore` if needed
- [ ] Test local startup with `docker-compose up -d`

### Task 1.2: Add Maven Dependencies
- [ ] Add `meilisearch-java` SDK dependency
- [ ] Add `testcontainers-meilisearch` test dependency
- [ ] Verify dependency resolution

### Task 1.3: Configuration Properties
- [ ] Create `MeilisearchProperties` configuration class
- [ ] Add properties to `application.yml` (disabled by default)
- [ ] Add properties to `application-local.yml` (enabled for dev)
- [ ] Document environment variables

---

## Phase 2: Backend Core Implementation

### Task 2.1: Meilisearch Client Configuration
- [ ] Create `MeilisearchConfig` configuration class
- [ ] Use `@ConditionalOnProperty` for optional loading
- [ ] Create `Client` and `Index` beans
- [ ] Add index initialization on startup (create if not exists)

### Task 2.2: Search Document Model
- [ ] Create `NewsItemSearchDocument` class
- [ ] Implement `from(NewsItem)` mapping method
- [ ] Handle null fields gracefully
- [ ] Flatten `NewsItemTags` into separate fields
- [ ] Convert timestamps to epoch seconds

### Task 2.3: Index Configuration Service
- [ ] Create `MeilisearchIndexService`
- [ ] Configure searchable attributes
- [ ] Configure filterable attributes (user_id, brief_id, etc.)
- [ ] Configure sortable attributes
- [ ] Configure ranking rules
- [ ] Run configuration on application startup

### Task 2.4: Search Service Interface
- [ ] Create `ArticleSearchService` interface
- [ ] Define `search(SearchRequest)` method
- [ ] Define `isAvailable()` method
- [ ] Create `SearchRequest` DTO with all filter params

### Task 2.5: Meilisearch Search Implementation
- [ ] Create `MeilisearchArticleSearchService`
- [ ] Implement filter string builder (CRITICAL: always include user_id)
- [ ] Implement sort string builder
- [ ] Map Meilisearch results to `NewsItemDTO`
- [ ] Handle pagination (offset/limit to Page)

### Task 2.6: PostgreSQL Fallback Search
- [ ] Create `PostgresArticleSearchService`
- [ ] Implement using existing repository methods
- [ ] Mark as fallback with `@ConditionalOnMissingBean`

### Task 2.7: Resilient Search Wrapper
- [ ] Create `ResilientSearchService` or add circuit breaker
- [ ] Implement fallback on Meilisearch failure
- [ ] Add logging for fallback events
- [ ] Consider using Resilience4j or simple try-catch

---

## Phase 3: Index Synchronization

### Task 3.1: Sync Service
- [ ] Create `MeilisearchSyncService`
- [ ] Implement `indexNewsItem(NewsItem)` - async
- [ ] Implement `updateNewsItem(NewsItem)` - async
- [ ] Implement `deleteNewsItem(UUID)` - async
- [ ] Implement `reindexBrief(UUID)` - batch operation
- [ ] Implement `reindexAll()` - full reindex (admin)

### Task 3.2: Domain Events
- [ ] Create `NewsItemCreatedEvent`
- [ ] Create `NewsItemUpdatedEvent`
- [ ] Create `NewsItemDeletedEvent`
- [ ] Publish events from `NewsItemService`

### Task 3.3: Event Listener
- [ ] Create `NewsItemIndexListener`
- [ ] Listen to domain events
- [ ] Use `@TransactionalEventListener(AFTER_COMMIT)`
- [ ] Call sync service methods
- [ ] Handle errors gracefully (log, don't throw)

### Task 3.4: Read/Saved Status Sync
- [ ] Update index when `readAt` changes
- [ ] Update index when `saved` changes
- [ ] Ensure these lightweight updates are fast

---

## Phase 4: API Integration

### Task 4.1: Update Controller
- [ ] Inject `ArticleSearchService` into `DayBriefController`
- [ ] Route search queries (when `q` param present) to search service
- [ ] Keep existing behavior when `q` is empty/null
- [ ] Ensure all existing filters still work

### Task 4.2: Security Validation
- [ ] Verify user_id is ALWAYS included in search filter
- [ ] Write security test for cross-user data access
- [ ] Write security test for cross-brief data access

---

## Phase 5: Admin & Operations

### Task 5.1: Health Indicator
- [ ] Create `MeilisearchHealthIndicator`
- [ ] Check Meilisearch connectivity
- [ ] Include in `/actuator/health` endpoint

### Task 5.2: Admin Endpoints (Optional)
- [ ] Create admin endpoint for reindex brief
- [ ] Create admin endpoint for index stats
- [ ] Secure with admin role

### Task 5.3: Startup Index Verification
- [ ] On startup, verify index exists
- [ ] Create index with settings if missing
- [ ] Log index statistics

---

## Phase 6: Testing

### Task 6.1: Unit Tests
- [ ] Test `NewsItemSearchDocument.from()` mapping
- [ ] Test filter string building
- [ ] Test sort string building
- [ ] Mock Meilisearch client

### Task 6.2: Integration Tests
- [ ] Set up Testcontainers for Meilisearch
- [ ] Test basic search functionality
- [ ] Test filter combinations
- [ ] Test pagination
- [ ] Test user isolation (CRITICAL)
- [ ] Test brief isolation

### Task 6.3: Fallback Tests
- [ ] Test app starts without Meilisearch configured
- [ ] Test fallback when Meilisearch unavailable
- [ ] Test existing PostgreSQL search still works

---

## Phase 7: Frontend (Optional Enhancements)

### Task 7.1: Search Input Improvements
- [ ] Add debounce (150ms) if not present
- [ ] Show loading indicator while searching
- [ ] Highlight matched terms in results (if API supports)

### Task 7.2: Empty State
- [ ] Show helpful message when no results
- [ ] Suggest removing filters if too restrictive

---

## Phase 8: Production Deployment

### Task 8.1: Production Configuration
- [ ] Create Meilisearch systemd service on Ubuntu
- [ ] Or: Add to production docker-compose
- [ ] Configure Nginx reverse proxy
- [ ] Set up HTTPS with Let's Encrypt

### Task 8.2: Secure API Keys
- [ ] Generate production master key
- [ ] Store in environment/secrets manager
- [ ] Optionally create search-only API key

### Task 8.3: Initial Data Migration
- [ ] Create migration script/command
- [ ] Run full reindex of existing articles
- [ ] Verify index completeness

### Task 8.4: Monitoring
- [ ] Add Meilisearch to monitoring
- [ ] Set up alerts for unavailability
- [ ] Monitor index size and query latency

---

## Acceptance Criteria

### Must Have
- [ ] Search returns results within 50ms
- [ ] Typo tolerance works (e.g., "tecnology" finds "technology")
- [ ] User A cannot see User B's articles
- [ ] Search within Brief X doesn't return Brief Y's articles
- [ ] All existing filters work with search
- [ ] App works when Meilisearch is disabled/unavailable

### Nice to Have
- [ ] Highlighted search matches
- [ ] Search suggestions
- [ ] Index statistics visible to admins

---

## Estimated Effort

| Phase | Tasks | Complexity |
|-------|-------|------------|
| Phase 1: Infrastructure | 3 | Low |
| Phase 2: Core Backend | 7 | Medium |
| Phase 3: Sync | 4 | Medium |
| Phase 4: API | 2 | Low |
| Phase 5: Operations | 3 | Low |
| Phase 6: Testing | 3 | Medium |
| Phase 7: Frontend | 2 | Low |
| Phase 8: Production | 4 | Medium |

**Recommended starting point:** Phase 1 → Phase 2 → Phase 3 → Phase 6 (in parallel with Phase 4)
