# Usage Tracking — Implementation Tasks

## Phase 1: Data Layer Foundation

**Goal**: Establish database schema and JPA entity for storing API usage logs.

**Verification**: Run `mvn test -Dtest=ApiUsageLogRepositoryTest` and verify entity persists correctly.

### Tasks

- [ ] **1.1** Create database migration `V16__Add_api_usage_logs.sql`
  - Table `api_usage_logs` with columns: id, user_id, feature_key, model, input_tokens, output_tokens, total_tokens, success, error_message, duration_ms, created_at
  - Foreign key to users with `ON DELETE SET NULL`
  - Indexes on user_id, feature_key, created_at
  - Composite index on (user_id, created_at DESC)

- [ ] **1.2** Create `AiFeature` enum
  - Location: `be.transcode.morningdeck.server.provider.ai.AiFeature`
  - Values: ENRICH, SCORE, ENRICH_SCORE, EMAIL_EXTRACT, WEB_EXTRACT, REPORT_GEN

- [ ] **1.3** Create `ApiUsageLog` entity
  - Location: `be.transcode.morningdeck.server.core.model.ApiUsageLog`
  - Fields matching migration schema
  - `@PrePersist` to set `createdAt`
  - `@Enumerated(EnumType.STRING)` for featureKey

- [ ] **1.4** Create `ApiUsageLogRepository`
  - Location: `be.transcode.morningdeck.server.core.repository.ApiUsageLogRepository`
  - Extends `JpaRepository<ApiUsageLog, UUID>`
  - Basic CRUD only for now (query methods in Phase 4)

- [ ] **1.5** Write `ApiUsageLogRepositoryTest`
  - Test entity persistence
  - Test nullable fields (user, tokens)
  - Test enum storage as string

---

## Phase 2: AI Service Tracking Infrastructure

**Goal**: Modify SpringAiService to expose token usage metadata and create the tracking decorator.

**Verification**: Run `mvn test -Dtest=TrackedAiServiceTest` and verify tracking captures timing, tokens, and feature keys.

### Tasks

- [ ] **2.1** Create `AiCallResult<T>` record
  - Location: `be.transcode.morningdeck.server.provider.ai.AiCallResult`
  - Fields: result (T), usage (Usage), model (String)

- [ ] **2.2** Create `AiUsageContext` ThreadLocal holder
  - Location: `be.transcode.morningdeck.server.provider.ai.AiUsageContext`
  - Static methods: setUserId, getUserId, clear

- [ ] **2.3** Add tracked variants to `SpringAiService`
  - Add helper method `parseEntityFromResponse(ChatResponse, Class<T>)` to extract entity from response content
  - Add `enrichTracked()` returning `AiCallResult<EnrichmentResult>`
  - Add `scoreTracked()` returning `AiCallResult<ScoreResult>`
  - Add `enrichWithScoreTracked()` (both overloads) returning `AiCallResult<EnrichmentWithScoreResult>`
  - Add `extractFromEmailTracked()` returning `AiCallResult<List<ExtractedNewsItem>>`
  - Add `extractFromWebTracked()` returning `AiCallResult<List<ExtractedWebItem>>`
  - Add `generateReportEmailContentTracked()` returning `AiCallResult<ReportEmailContent>`
  - Each uses `.chatResponse()` instead of `.entity()` and extracts Usage from metadata

- [ ] **2.4** Create `ApiUsageLogService`
  - Location: `be.transcode.morningdeck.server.core.service.ApiUsageLogService`
  - Method: `logAsync(UUID userId, AiFeature feature, String model, Usage usage, boolean success, String errorMessage, long durationMs)`
  - Annotate with `@Async`
  - Swallow exceptions (log error but don't propagate)
  - Truncate errorMessage to 1024 chars

- [ ] **2.5** Create `TrackedAiService` decorator
  - Location: `be.transcode.morningdeck.server.provider.ai.TrackedAiService`
  - Annotate with `@Service` and `@Primary`
  - Inject `SpringAiService` (not AiService to avoid circular)
  - Inject `ApiUsageLogService`
  - Implement all `AiService` methods
  - Each method: capture start time, get userId from AiUsageContext, call delegate tracked method, log result

- [ ] **2.6** Write `TrackedAiServiceTest`
  - Mock SpringAiService and ApiUsageLogService
  - Verify timing is captured (durationMs > 0)
  - Verify feature key mapping for each method
  - Verify user context propagation
  - Verify error handling (success=false, errorMessage captured)
  - Verify null usage handling

- [ ] **2.7** Write `ApiUsageLogServiceTest`
  - Mock repository
  - Verify entity construction from Usage
  - Verify null handling for missing usage
  - Verify error message truncation

---

## Phase 3: Call Site Integration

**Goal**: Update all AI service call sites to set user context before calls.

**Verification**: Run full processing pipeline manually and verify logs created in database with correct user IDs.

### Tasks

- [ ] **3.1** Update `ProcessingWorker.doProcess()`
  - Extract userId from `item.getSource().getDayBrief().getUserId()`
  - Wrap AI call in try/finally with AiUsageContext.setUserId/clear

- [ ] **3.2** Update `EmailIngestionListener.processEmail()`
  - Extract userId from `source.getDayBrief().getUserId()`
  - Wrap AI call in try/finally with AiUsageContext.setUserId/clear

- [ ] **3.3** Update `WebFetcher.fetch()`
  - Extract userId from `source.getDayBrief().getUserId()`
  - Wrap AI call in try/finally with AiUsageContext.setUserId/clear

- [ ] **3.4** Update `ReportEmailDeliveryService.sendReportEmail()`
  - Extract userId from `dayBrief.getUserId()`
  - Wrap AI call in try/finally with AiUsageContext.setUserId/clear

- [ ] **3.5** Write `ApiUsageLogIT` integration test
  - Test end-to-end: trigger ProcessingWorker with mock AI → verify log created
  - Verify user association correct
  - Verify feature key correct
  - Verify timing captured

---

## Phase 4: Admin Query API

**Goal**: Expose usage data via admin-only API endpoints.

**Verification**: Run `mvn test -Dtest=AdminUsageApiIT` and manually test endpoints via Swagger.

### Tasks

- [ ] **4.1** Create `ApiUsageLogDTO`
  - Location: `be.transcode.morningdeck.server.core.dto.ApiUsageLogDTO`
  - Fields: id, userId, username, featureKey, model, inputTokens, outputTokens, totalTokens, success, errorMessage, durationMs, createdAt
  - Use `@JsonInclude(NON_NULL)`

- [ ] **4.2** Create `UsageSummaryDTO`
  - Location: `be.transcode.morningdeck.server.core.dto.UsageSummaryDTO`
  - Fields: totalCalls, successfulCalls, failedCalls, totalInputTokens, totalOutputTokens, totalTokens, avgDurationMs
  - Nested class `FeatureUsage` with calls, inputTokens, outputTokens, totalTokens, avgDurationMs
  - Field `byFeature` as `Map<String, FeatureUsage>`

- [ ] **4.3** Add query methods to `ApiUsageLogRepository`
  - `findAllByFilters(UUID userId, AiFeature feature, Instant from, Instant to, Pageable pageable)` using Specification or @Query
  - `getSummary(UUID userId, Instant from, Instant to)` returning aggregates
  - `getSummaryByFeature(UUID userId, Instant from, Instant to)` returning per-feature breakdown

- [ ] **4.4** Add methods to `AdminService`
  - `getUsageLogs(UUID userId, AiFeature feature, Instant from, Instant to, Pageable pageable)` returning `Page<ApiUsageLogDTO>`
  - `getUsageSummary(UUID userId, Instant from, Instant to)` returning `UsageSummaryDTO`
  - Handle null filters (defaults: from=30 days ago, to=now)

- [ ] **4.5** Add endpoints to `AdminController`
  - `GET /admin/usage` - paginated logs with filters (userId, feature, from, to, page, size)
  - `GET /admin/usage/summary` - aggregated stats with filters (userId, from, to)
  - Both require ADMIN role (existing @PreAuthorize on class)

- [ ] **4.6** Write `AdminUsageApiIT`
  - Test admin access required (403 for regular user)
  - Test pagination
  - Test filters (userId, feature, date range)
  - Test summary aggregation accuracy

---

## Phase 5: Polish and Observability

**Goal**: Add metrics, logging, and cleanup.

**Verification**: Check application logs show usage tracking info; verify metrics exposed at `/actuator/metrics`.

### Tasks

- [ ] **5.1** Add structured logging to `TrackedAiService`
  - INFO log on each call: feature, userId, tokens, durationMs
  - WARN log when usage metadata is null
  - DEBUG log with full details

- [ ] **5.2** Add Micrometer metrics to `TrackedAiService`
  - Counter: `ai.calls.total` with tag `feature`
  - Counter: `ai.calls.errors` with tag `feature`
  - Counter: `ai.tokens.input` with tag `feature`
  - Counter: `ai.tokens.output` with tag `feature`
  - Timer: `ai.call.duration` with tag `feature`

- [ ] **5.3** Update OpenAPI documentation
  - Add descriptions to new admin endpoints
  - Document query parameters and response schemas

- [ ] **5.4** Final cleanup
  - Review all TODO comments
  - Ensure consistent code style
  - Update CLAUDE.md if needed with new components

---

## File Checklist

### New Files
- `backend/src/main/resources/db/migrations/V16__Add_api_usage_logs.sql`
- `backend/src/main/java/be/transcode/morningdeck/server/provider/ai/AiFeature.java`
- `backend/src/main/java/be/transcode/morningdeck/server/provider/ai/AiCallResult.java`
- `backend/src/main/java/be/transcode/morningdeck/server/provider/ai/AiUsageContext.java`
- `backend/src/main/java/be/transcode/morningdeck/server/provider/ai/TrackedAiService.java`
- `backend/src/main/java/be/transcode/morningdeck/server/core/model/ApiUsageLog.java`
- `backend/src/main/java/be/transcode/morningdeck/server/core/repository/ApiUsageLogRepository.java`
- `backend/src/main/java/be/transcode/morningdeck/server/core/service/ApiUsageLogService.java`
- `backend/src/main/java/be/transcode/morningdeck/server/core/dto/ApiUsageLogDTO.java`
- `backend/src/main/java/be/transcode/morningdeck/server/core/dto/UsageSummaryDTO.java`
- `backend/src/test/java/be/transcode/morningdeck/server/core/repository/ApiUsageLogRepositoryTest.java`
- `backend/src/test/java/be/transcode/morningdeck/server/provider/ai/TrackedAiServiceTest.java`
- `backend/src/test/java/be/transcode/morningdeck/server/core/service/ApiUsageLogServiceTest.java`
- `backend/src/test/java/be/transcode/morningdeck/server/core/controller/AdminUsageApiIT.java`
- `backend/src/test/java/be/transcode/morningdeck/server/core/controller/ApiUsageLogIT.java`

### Modified Files
- `backend/src/main/java/be/transcode/morningdeck/server/provider/ai/SpringAiService.java`
- `backend/src/main/java/be/transcode/morningdeck/server/core/queue/ProcessingWorker.java`
- `backend/src/main/java/be/transcode/morningdeck/server/core/service/EmailIngestionListener.java`
- `backend/src/main/java/be/transcode/morningdeck/server/provider/sourcefetch/WebFetcher.java`
- `backend/src/main/java/be/transcode/morningdeck/server/core/service/ReportEmailDeliveryService.java`
- `backend/src/main/java/be/transcode/morningdeck/server/core/service/AdminService.java`
- `backend/src/main/java/be/transcode/morningdeck/server/core/controller/AdminController.java`
