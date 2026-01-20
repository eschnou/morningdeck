# Usage Tracking — Design Document

## 1. Overview

Implement internal auditing for all AI API calls by wrapping the `AiService` with a decorator that captures timing, token usage, user context, and feature attribution. Data stored in a new `api_usage_logs` table, queryable via admin API.

**Design Approach**: Decorator pattern around `AiService` to intercept all calls without modifying existing call sites.

## 2. Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Call Sites                                   │
│  ProcessingWorker │ EmailIngestionListener │ WebFetcher │ Report... │
└─────────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                  TrackedAiService (Decorator)                       │
│  - Captures start time                                              │
│  - Resolves user from AiUsageContext (ThreadLocal)                  │
│  - Delegates to wrapped AiService                                   │
│  - Extracts token usage from ChatResponse                           │
│  - Logs to ApiUsageLogRepository (async)                            │
└─────────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     SpringAiService                                 │
│  - Modified to return ChatResponse (not just entity)                │
│  - Extracts entity + preserves metadata                             │
└─────────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                   OpenAI API (via Spring AI)                        │
└─────────────────────────────────────────────────────────────────────┘
```

## 3. Components and Interfaces

### 3.1 New Components

#### `AiFeature` (Enum)
```java
package be.transcode.morningdeck.server.provider.ai;

public enum AiFeature {
    ENRICH,
    SCORE,
    ENRICH_SCORE,
    EMAIL_EXTRACT,
    WEB_EXTRACT,
    REPORT_GEN
}
```

#### `AiUsageContext` (ThreadLocal holder)
```java
package be.transcode.morningdeck.server.provider.ai;

import java.util.UUID;

/**
 * ThreadLocal context for tracking which user triggered the AI call.
 * Must be set by callers before invoking AiService methods.
 */
public class AiUsageContext {
    private static final ThreadLocal<UUID> currentUserId = new ThreadLocal<>();

    public static void setUserId(UUID userId) {
        currentUserId.set(userId);
    }

    public static UUID getUserId() {
        return currentUserId.get();
    }

    public static void clear() {
        currentUserId.remove();
    }
}
```

#### `AiCallResult<T>` (Internal wrapper)
```java
package be.transcode.morningdeck.server.provider.ai;

import org.springframework.ai.chat.metadata.Usage;

/**
 * Internal result wrapper that carries both the parsed entity and token usage metadata.
 */
public record AiCallResult<T>(
    T result,
    Usage usage,
    String model
) {}
```

#### `TrackedAiService` (Decorator)
```java
package be.transcode.morningdeck.server.provider.ai;

/**
 * Decorator that wraps AiService to track usage metrics.
 * - Captures timing (duration_ms)
 * - Extracts token usage from ChatResponse
 * - Logs to ApiUsageLog asynchronously
 */
@Service
@Primary
@RequiredArgsConstructor
public class TrackedAiService implements AiService {
    private final SpringAiService delegate;
    private final ApiUsageLogService usageLogService;

    @Override
    public EnrichmentResult enrich(String title, String content) {
        return trackCall(AiFeature.ENRICH, () -> delegate.enrichTracked(title, content));
    }

    // ... similar for all methods

    private <T> T trackCall(AiFeature feature, Supplier<AiCallResult<T>> call) {
        UUID userId = AiUsageContext.getUserId();
        long startTime = System.currentTimeMillis();
        boolean success = true;
        String errorMessage = null;
        Usage usage = null;
        String model = null;

        try {
            AiCallResult<T> result = call.get();
            usage = result.usage();
            model = result.model();
            return result.result();
        } catch (Exception e) {
            success = false;
            errorMessage = e.getMessage();
            throw e;
        } finally {
            long durationMs = System.currentTimeMillis() - startTime;
            usageLogService.logAsync(userId, feature, model, usage, success, errorMessage, durationMs);
        }
    }
}
```

#### `ApiUsageLogService`
```java
package be.transcode.morningdeck.server.core.service;

/**
 * Service for logging API usage asynchronously.
 */
@Service
@RequiredArgsConstructor
public class ApiUsageLogService {
    private final ApiUsageLogRepository repository;
    private final UserRepository userRepository;

    @Async
    public void logAsync(UUID userId, AiFeature feature, String model,
                         Usage usage, boolean success, String errorMessage, long durationMs) {
        try {
            User user = userId != null ? userRepository.findById(userId).orElse(null) : null;

            ApiUsageLog log = ApiUsageLog.builder()
                .user(user)
                .featureKey(feature)
                .model(model)
                .inputTokens(usage != null ? usage.getPromptTokens() : null)
                .outputTokens(usage != null ? usage.getGenerationTokens() : null)
                .totalTokens(usage != null ? usage.getTotalTokens() : null)
                .success(success)
                .errorMessage(truncate(errorMessage, 1024))
                .durationMs(durationMs)
                .build();

            repository.save(log);
        } catch (Exception e) {
            log.error("Failed to log API usage: {}", e.getMessage());
            // Swallow - logging failure must not affect main flow
        }
    }
}
```

### 3.2 Modified Components

#### `SpringAiService` Changes

Add "tracked" variants that return `AiCallResult<T>` with usage metadata:

```java
public AiCallResult<EnrichmentResult> enrichTracked(String title, String content) {
    ChatResponse response = chatClient.prompt()
        .user(u -> u.text(loadPrompt(enrichPromptResource))
            .param("title", title)
            .param("content", truncate(content, 4000)))
        .call()
        .chatResponse();

    EnrichmentResult entity = parseEntity(response, EnrichmentResult.class);
    Usage usage = response.getMetadata().getUsage();
    String model = response.getMetadata().getModel();

    return new AiCallResult<>(entity, usage, model);
}
```

#### Call Site Changes

Each call site must set `AiUsageContext` before calling `AiService`:

**ProcessingWorker.java**
```java
private void doProcess(NewsItem item) {
    UUID userId = item.getSource().getDayBrief().getUserId();
    try {
        AiUsageContext.setUserId(userId);
        // ... existing code calling aiService
    } finally {
        AiUsageContext.clear();
    }
}
```

**EmailIngestionListener.java**
```java
private void processEmail(Source source, EmailMessage email) {
    UUID userId = source.getDayBrief().getUserId();
    try {
        AiUsageContext.setUserId(userId);
        // ... existing code
    } finally {
        AiUsageContext.clear();
    }
}
```

**WebFetcher.java**
```java
public List<FetchedItem> fetch(Source source) {
    UUID userId = source.getDayBrief().getUserId();
    try {
        AiUsageContext.setUserId(userId);
        // ... existing code
    } finally {
        AiUsageContext.clear();
    }
}
```

**ReportEmailDeliveryService.java**
```java
public void sendReportEmail(DailyReport report) {
    UUID userId = dayBrief.getUserId();
    try {
        AiUsageContext.setUserId(userId);
        // ... existing code
    } finally {
        AiUsageContext.clear();
    }
}
```

### 3.3 Admin API

#### `AdminController` additions

```java
@GetMapping("/usage")
public ResponseEntity<Page<ApiUsageLogDTO>> getUsageLogs(
    @RequestParam(required = false) UUID userId,
    @RequestParam(required = false) AiFeature feature,
    @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Instant from,
    @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Instant to,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "50") int size) {

    return ResponseEntity.ok(adminService.getUsageLogs(userId, feature, from, to,
        PageRequest.of(page, size, Sort.by("createdAt").descending())));
}

@GetMapping("/usage/summary")
public ResponseEntity<UsageSummaryDTO> getUsageSummary(
    @RequestParam(required = false) UUID userId,
    @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Instant from,
    @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Instant to) {

    return ResponseEntity.ok(adminService.getUsageSummary(userId, from, to));
}
```

## 4. Data Models

### 4.1 Entity: `ApiUsageLog`

```java
package be.transcode.morningdeck.server.core.model;

@Entity
@Table(name = "api_usage_logs", indexes = {
    @Index(name = "idx_api_usage_user_id", columnList = "user_id"),
    @Index(name = "idx_api_usage_feature", columnList = "feature_key"),
    @Index(name = "idx_api_usage_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiUsageLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_id")  // nullable - for system calls
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_key", nullable = false, length = 32)
    private AiFeature featureKey;

    @Column(length = 64)
    private String model;

    @Column(name = "input_tokens")
    private Long inputTokens;

    @Column(name = "output_tokens")
    private Long outputTokens;

    @Column(name = "total_tokens")
    private Long totalTokens;

    @Column(nullable = false)
    private Boolean success;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Column(name = "duration_ms", nullable = false)
    private Long durationMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
```

### 4.2 Database Migration: `V16__Add_api_usage_logs.sql`

```sql
CREATE TABLE api_usage_logs (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    feature_key VARCHAR(32) NOT NULL,
    model VARCHAR(64),
    input_tokens BIGINT,
    output_tokens BIGINT,
    total_tokens BIGINT,
    success BOOLEAN NOT NULL,
    error_message VARCHAR(1024),
    duration_ms BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_api_usage_user_id ON api_usage_logs(user_id);
CREATE INDEX idx_api_usage_feature ON api_usage_logs(feature_key);
CREATE INDEX idx_api_usage_created_at ON api_usage_logs(created_at);

-- Composite index for common query pattern
CREATE INDEX idx_api_usage_user_created ON api_usage_logs(user_id, created_at DESC);
```

### 4.3 DTOs

#### `ApiUsageLogDTO`
```java
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiUsageLogDTO {
    private UUID id;
    private UUID userId;
    private String username;
    private String featureKey;
    private String model;
    private Long inputTokens;
    private Long outputTokens;
    private Long totalTokens;
    private Boolean success;
    private String errorMessage;
    private Long durationMs;
    private Instant createdAt;
}
```

#### `UsageSummaryDTO`
```java
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UsageSummaryDTO {
    private Long totalCalls;
    private Long successfulCalls;
    private Long failedCalls;
    private Long totalInputTokens;
    private Long totalOutputTokens;
    private Long totalTokens;
    private Long avgDurationMs;
    private Map<String, FeatureUsage> byFeature;

    @Data
    public static class FeatureUsage {
        private Long calls;
        private Long inputTokens;
        private Long outputTokens;
        private Long totalTokens;
        private Long avgDurationMs;
    }
}
```

### 4.4 Repository

```java
package be.transcode.morningdeck.server.core.repository;

public interface ApiUsageLogRepository extends JpaRepository<ApiUsageLog, UUID> {

    Page<ApiUsageLog> findByUserIdAndFeatureKeyAndCreatedAtBetween(
        UUID userId, AiFeature feature, Instant from, Instant to, Pageable pageable);

    @Query("""
        SELECT new map(
            COUNT(*) as totalCalls,
            SUM(CASE WHEN a.success = true THEN 1 ELSE 0 END) as successfulCalls,
            COALESCE(SUM(a.inputTokens), 0) as totalInputTokens,
            COALESCE(SUM(a.outputTokens), 0) as totalOutputTokens,
            COALESCE(SUM(a.totalTokens), 0) as totalTokens,
            AVG(a.durationMs) as avgDurationMs
        )
        FROM ApiUsageLog a
        WHERE (:userId IS NULL OR a.user.id = :userId)
        AND a.createdAt BETWEEN :from AND :to
        """)
    Map<String, Object> getSummary(UUID userId, Instant from, Instant to);

    @Query("""
        SELECT a.featureKey as feature,
               COUNT(*) as calls,
               COALESCE(SUM(a.inputTokens), 0) as inputTokens,
               COALESCE(SUM(a.outputTokens), 0) as outputTokens,
               COALESCE(SUM(a.totalTokens), 0) as totalTokens,
               AVG(a.durationMs) as avgDurationMs
        FROM ApiUsageLog a
        WHERE (:userId IS NULL OR a.user.id = :userId)
        AND a.createdAt BETWEEN :from AND :to
        GROUP BY a.featureKey
        """)
    List<Object[]> getSummaryByFeature(UUID userId, Instant from, Instant to);
}
```

## 5. Error Handling

| Scenario | Handling |
|----------|----------|
| Token usage unavailable | Store null values (not zeros) |
| User not in context | Store null user_id, log warning |
| Logging fails | Swallow exception, log error |
| AI call fails | Log with success=false, capture error message |
| Database unavailable | @Async logging fails silently, main flow continues |

## 6. Testing Strategy

### Unit Tests

1. **TrackedAiServiceTest**
   - Verify timing captured correctly
   - Verify feature key mapping
   - Verify user context propagation
   - Verify error handling (success=false on exception)
   - Verify null handling for missing usage data

2. **ApiUsageLogServiceTest**
   - Verify async logging does not block
   - Verify entity construction from Usage
   - Verify error message truncation

### Integration Tests

1. **ApiUsageLogIT**
   - End-to-end flow: call AiService → verify log created
   - Query API with filters
   - Summary aggregation accuracy
   - User context propagation through ProcessingWorker

2. **AdminUsageApiIT**
   - Admin-only access enforcement
   - Pagination
   - Filter combinations

## 7. Performance Considerations

| Concern | Mitigation |
|---------|------------|
| Logging overhead | Async via `@Async` - non-blocking |
| Database writes | Batch writes not needed at 100 calls/sec |
| Query performance | Indexes on user_id, feature_key, created_at |
| Memory (ThreadLocal) | Always clear in finally block |
| Large result sets | Paginated queries, no unbounded fetches |

### Estimated Overhead
- ThreadLocal set/get: ~10ns
- System.currentTimeMillis(): ~100ns
- Async log submission: ~1ms
- **Total added latency**: <5ms per call

## 8. Security Considerations

| Concern | Mitigation |
|---------|------------|
| Data access | Admin-only endpoints via `@PreAuthorize("hasRole('ADMIN')")` |
| Sensitive data | No prompt/response content stored |
| User deletion | `ON DELETE SET NULL` - preserves audit trail |
| API exposure | No user-facing endpoints for usage data |

## 9. Monitoring and Observability

### Metrics (via Micrometer)
```java
// In TrackedAiService
@Timed(value = "ai.call", extraTags = {"feature", "..."})
private <T> T trackCall(AiFeature feature, ...) {
    meterRegistry.counter("ai.calls.total", "feature", feature.name()).increment();
    meterRegistry.counter("ai.tokens.input", "feature", feature.name())
        .increment(usage.getPromptTokens());
    // ...
}
```

### Key Metrics
- `ai.calls.total` - Counter by feature
- `ai.calls.errors` - Counter by feature, error type
- `ai.tokens.input` - Counter by feature
- `ai.tokens.output` - Counter by feature
- `ai.call.duration` - Timer by feature

### Logs
- INFO: Successful call summary (feature, tokens, duration)
- WARN: Missing usage metadata
- ERROR: Logging failures (non-blocking)

## 10. Implementation Order

1. Database migration (`V16__Add_api_usage_logs.sql`)
2. Entity and repository (`ApiUsageLog`, `ApiUsageLogRepository`)
3. Enums and context (`AiFeature`, `AiUsageContext`, `AiCallResult`)
4. Modify `SpringAiService` to expose tracked variants
5. Implement `ApiUsageLogService`
6. Implement `TrackedAiService` decorator
7. Update call sites to set `AiUsageContext`
8. Add admin API endpoints
9. Add metrics
10. Write tests

## 11. References

- [Spring AI ChatResponse Usage](https://docs.spring.io/spring-ai/reference/api/usage-handling.html)
- [Spring AI Chat Client API](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
