# Credit-Per-Item: Design Document

## Overview

This feature implements credit enforcement across the news processing pipeline. Credits are deducted when news items undergo AI processing, and all AI-dependent operations are blocked when credits reach zero.

**Key Design Principles:**
- Block at scheduler level for efficiency (batch queries, not per-item checks)
- Defense-in-depth: safety net in `TrackedAiService` as last resort
- Non-destructive blocking: items stay in current status, resume when credits replenished
- Single credit deduction point: `ProcessingWorker` after successful AI enrichment

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           CREDIT ENFORCEMENT LAYERS                              │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  LAYER 1: SCHEDULER BLOCKING (Primary)                                          │
│  ┌─────────────────────┐  ┌─────────────────────┐  ┌─────────────────────┐      │
│  │ FeedSchedulerJob    │  │ProcessingSchedulerJob│  │BriefingSchedulerJob│      │
│  │ ─────────────────── │  │ ─────────────────── │  │ ─────────────────── │      │
│  │ Skip sources for    │  │ Skip items for      │  │ Skip briefings for  │      │
│  │ users with 0 credits│  │ users with 0 credits│  │ users with 0 credits│      │
│  └─────────────────────┘  └─────────────────────┘  └─────────────────────┘      │
│                                                                                  │
│  LAYER 2: LISTENER BLOCKING                                                      │
│  ┌─────────────────────┐                                                         │
│  │EmailIngestionListener│                                                        │
│  │ ─────────────────── │                                                         │
│  │ Check credits before │                                                        │
│  │ AI extraction        │                                                        │
│  └─────────────────────┘                                                         │
│                                                                                  │
│  LAYER 3: API BLOCKING                                                           │
│  ┌─────────────────────┐                                                         │
│  │DayBriefController   │                                                         │
│  │ ─────────────────── │                                                         │
│  │ Check credits before │                                                        │
│  │ manual execution     │                                                        │
│  └─────────────────────┘                                                         │
│                                                                                  │
│  LAYER 4: SAFETY NET (Defense-in-Depth)                                          │
│  ┌─────────────────────┐                                                         │
│  │ TrackedAiService    │                                                         │
│  │ ─────────────────── │                                                         │
│  │ Throw exception if   │                                                        │
│  │ AI call with 0 credit│                                                        │
│  └─────────────────────┘                                                         │
│                                                                                  │
│  CREDIT DEDUCTION (existing SubscriptionService)                                 │
│  ┌─────────────────────┐                                                         │
│  │ ProcessingWorker    │ ──> SubscriptionService.useCredits() ──> CreditUsageLog │
│  └─────────────────────┘                                                         │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

## Components and Interfaces

### 1. SubscriptionService (Extended)

Extend existing service with additional methods for credit checking.

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/service/SubscriptionService.java`

**Existing methods (no changes):**
- `useCredits(User user, int credits)` - deducts credits and logs to `CreditUsageLog`
- `createSubscription()`, `upgradePlan()`, `checkAndRenewSubscriptions()`

**New methods to add:**

```java
/**
 * Check if user has any credits (> 0).
 * @param userId User ID
 * @return true if user has at least 1 credit
 */
public boolean hasCredits(UUID userId) {
    return subscriptionRepository.findCreditsBalanceByUserId(userId)
            .map(balance -> balance > 0)
            .orElse(false);
}

/**
 * Get credit balance for user.
 * @return credit balance or 0 if subscription not found
 */
public int getCreditsBalance(UUID userId) {
    return subscriptionRepository.findCreditsBalanceByUserId(userId).orElse(0);
}

/**
 * Get set of user IDs that have credits > 0.
 * Used by schedulers for efficient batch filtering.
 */
public Set<UUID> getUserIdsWithCredits() {
    return subscriptionRepository.findUserIdsWithCredits();
}

/**
 * Overload of useCredits that accepts userId instead of User entity.
 * Also triggers no-credits notification when balance hits zero.
 */
@Transactional
public boolean useCredits(UUID userId, int credits) {
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    boolean success = useCredits(user, credits);

    // Send notification if balance just hit zero
    if (success) {
        Subscription sub = user.getSubscription();
        if (sub.getCreditsBalance() == 0) {
            sendNoCreditsNotification(user);
        }
    }

    return success;
}

private void sendNoCreditsNotification(User user) {
    try {
        emailService.sendNoCreditsEmail(user.getEmail(), user.getName(), domain);
        log.info("Sent no-credits notification to user {}", user.getId());
    } catch (Exception e) {
        log.error("Failed to send no-credits notification to user {}: {}", user.getId(), e.getMessage());
    }
}
```

### 2. InsufficientCreditsException (New)

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/exception/InsufficientCreditsException.java`

```java
public class InsufficientCreditsException extends RuntimeException {
    private final UUID userId;
    private final int creditsRequired;
    private final int creditsAvailable;

    public InsufficientCreditsException(UUID userId, int required, int available) {
        super(String.format("Insufficient credits for user %s: required=%d, available=%d",
            userId, required, available));
        this.userId = userId;
        this.creditsRequired = required;
        this.creditsAvailable = available;
    }
}
```

### 3. Modified Components

#### 3.1 TrackedAiService (Safety Net)

Add credit check at the start of `trackCall()` method:

```java
private <T> T trackCall(AiFeature feature, Supplier<AiCallResult<T>> call) {
    UUID userId = AiUsageContext.getUserId();

    // Safety net: verify credits if user context is set
    if (userId != null) {
        int balance = subscriptionService.getCreditsBalance(userId);
        if (balance <= 0) {
            log.error("AI call attempted with zero credits: userId={} feature={}", userId, feature);
            throw new InsufficientCreditsException(userId, 1, balance);
        }
    }

    // ... existing tracking logic
}
```

#### 3.2 ProcessingWorker (Credit Deduction)

Add credit deduction after successful AI processing:

```java
private void doProcess(NewsItem item) {
    // ... existing content preparation ...

    UUID userId = item.getSource().getDayBrief().getUserId();
    try {
        AiUsageContext.setUserId(userId);

        // Enrich and score
        EnrichmentWithScoreResult result = aiService.enrichWithScore(...);

        // Apply results to item
        item.setSummary(result.summary());
        // ... set tags, score, etc.

        // Deduct credit after successful processing
        boolean deducted = subscriptionService.useCredits(userId, 1);
        if (!deducted) {
            // This should not happen due to scheduler-level checks
            log.error("Failed to deduct credit after AI processing: userId={}", userId);
            throw new InsufficientCreditsException(userId, 1, 0);
        }

    } finally {
        AiUsageContext.clear();
    }
}
```

#### 3.3 ProcessingSchedulerJob

Filter out items for users with zero credits:

```java
@Scheduled(fixedRateString = "${application.jobs.news-processing.interval:60000}")
public void scheduleProcessing() {
    if (!processingQueue.canAccept()) {
        log.warn("Queue full, skipping scheduling cycle");
        return;
    }

    // Get users with available credits
    Set<UUID> usersWithCredits = subscriptionService.getUserIdsWithCredits();
    if (usersWithCredits.isEmpty()) {
        log.debug("No users with credits available");
        return;
    }

    // Find items for users with credits
    List<NewsItem> candidates = newsItemRepository.findItemsForProcessingByUserIds(
            usersWithCredits, PageRequest.of(0, batchSize));

    // ... rest of scheduling logic
}
```

#### 3.4 FeedSchedulerJob

Filter out sources for users with zero credits:

```java
@Scheduled(fixedRateString = "${application.jobs.feed-scheduling.interval:60000}")
@Transactional
public void scheduleFeeds() {
    if (!fetchQueue.canAccept()) {
        return;
    }

    // Get users with credits
    Set<UUID> usersWithCredits = subscriptionService.getUserIdsWithCredits();

    // Filter candidates by users with credits
    List<Source> dueForRefresh = candidates.stream()
            .filter(s -> isDueForRefresh(s, now))
            .filter(s -> usersWithCredits.contains(s.getDayBrief().getUserId()))
            .limit(batchSize)
            .toList();

    // ... rest of scheduling logic
}
```

#### 3.5 BriefingSchedulerJob

Filter out briefings for users with zero credits:

```java
@Scheduled(fixedRateString = "${application.jobs.briefing-execution.interval:60000}")
public void scheduleBriefings() {
    if (!briefingQueue.canAccept()) {
        return;
    }

    // Get users with credits
    Set<UUID> usersWithCredits = subscriptionService.getUserIdsWithCredits();

    List<DayBrief> dueBriefings = candidateBriefings.stream()
            .filter(briefing -> isBriefingDue(briefing, nowUtc))
            .filter(briefing -> usersWithCredits.contains(briefing.getUserId()))
            .toList();

    // ... rest of scheduling logic
}
```

#### 3.6 EmailIngestionListener

Check credits before AI extraction:

```java
private void processEmail(Source source, EmailMessage email) {
    UUID userId = source.getDayBrief().getUserId();

    // Check credits before AI extraction
    if (!subscriptionService.hasCredits(userId)) {
        log.warn("Email received but user {} has no credits. Stored but not processed: messageId={}",
                userId, email.getMessageId());
        markRawEmailAsUnprocessed(source, email);
        return;
    }

    try {
        AiUsageContext.setUserId(userId);
        List<ExtractedNewsItem> items = aiService.extractFromEmail(email.getSubject(), email.getContent());
        // ... create news items
    } finally {
        AiUsageContext.clear();
    }
}
```

#### 3.7 DayBriefController

Check credits for manual briefing execution:

```java
@PostMapping("/{id}/execute")
public ResponseEntity<DailyReportDTO> executeBriefing(
        @AuthenticationPrincipal UserDetails userDetails,
        @PathVariable UUID id) {

    User user = userService.getInternalUserByUsername(userDetails.getUsername());

    // Check credits
    if (!subscriptionService.hasCredits(user.getId())) {
        throw new InsufficientCreditsException(user.getId(), 1, 0);
    }

    DayBrief dayBrief = dayBriefService.getDayBriefEntity(user.getId(), id);
    DailyReportDTO report = briefingWorker.executeManually(dayBrief);

    return ResponseEntity.ok(report);
}
```

#### 3.8 GlobalExceptionHandler

Handle `InsufficientCreditsException`:

```java
@ExceptionHandler(InsufficientCreditsException.class)
public ResponseEntity<ApiError> handleInsufficientCredits(
        InsufficientCreditsException ex, HttpServletRequest request) {
    return buildErrorResponse(HttpStatus.PAYMENT_REQUIRED, ex.getMessage(), request);
}
```

## Data Models

### Existing Models (No Changes)

- **Subscription** - `creditsBalance`, `monthlyCredits` fields already exist
- **CreditUsageLog** - Already tracks credit usage with `user`, `subscription`, `creditsUsed`, `usedAt`

### Modified Models

#### RawEmail (Add Processing Status)

For REQ-9 (optional email reprocessing), add a status field:

```java
@Entity
@Table(name = "raw_emails")
public class RawEmail {
    // ... existing fields ...

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false)
    @Builder.Default
    private RawEmailStatus processingStatus = RawEmailStatus.PENDING;
}

public enum RawEmailStatus {
    PENDING,     // Awaiting processing
    PROCESSED,   // Successfully processed
    SKIPPED      // Skipped due to insufficient credits
}
```

**Migration:** `V__add_raw_email_status.sql`
```sql
ALTER TABLE raw_emails ADD COLUMN processing_status VARCHAR(20) DEFAULT 'PROCESSED' NOT NULL;
CREATE INDEX idx_raw_emails_status ON raw_emails(processing_status) WHERE processing_status = 'SKIPPED';
```

### New Repository Methods

#### SubscriptionRepository

```java
@Query("SELECT s.user.id FROM Subscription s WHERE s.creditsBalance > 0")
Set<UUID> findUserIdsWithCredits();

@Query("SELECT s.creditsBalance FROM Subscription s WHERE s.user.id = :userId")
Optional<Integer> findCreditsBalanceByUserId(@Param("userId") UUID userId);
```

#### NewsItemRepository

```java
@Query("""
    SELECT n FROM NewsItem n
    WHERE n.status = be.transcode.morningdeck.server.core.model.NewsItemStatus.NEW
      AND n.source.dayBrief.userId IN :userIds
    ORDER BY n.createdAt ASC
    """)
List<NewsItem> findItemsForProcessingByUserIds(
        @Param("userIds") Set<UUID> userIds,
        Pageable pageable);
```

#### RawEmailRepository (for REQ-9)

```java
List<RawEmail> findByProcessingStatus(RawEmailStatus status);

@Query("SELECT r FROM RawEmail r WHERE r.processingStatus = 'SKIPPED' AND r.source.dayBrief.userId = :userId")
List<RawEmail> findSkippedByUserId(@Param("userId") UUID userId);
```

## Error Handling

| Exception | HTTP Status | Trigger |
|-----------|-------------|---------|
| `InsufficientCreditsException` | 402 Payment Required | Manual briefing execution, safety net |
| N/A (silent skip) | N/A | Scheduler skips items/sources/briefings |

**Logging Strategy:**
- `INFO`: Items/sources/briefings skipped due to zero credits
- `WARN`: Email stored but not processed due to zero credits
- `ERROR`: AI call attempted despite zero credits (safety net triggered)

## Testing Strategy

### Unit Tests

| Component | Test Cases |
|-----------|------------|
| `SubscriptionService` | hasCredits(), useCredits(UUID), getUserIdsWithCredits(), notification trigger |
| `ProcessingWorker` | Credit deduction after success, handling deduction failure |
| `TrackedAiService` | Safety net throws exception when credits = 0 |

### Integration Tests

| Test Class | Scenarios |
|------------|-----------|
| `CreditEnforcementIT` | End-to-end: create items, process, verify credit deduction |
| `SchedulerCreditIT` | Scheduler skips users with zero credits |
| `EmailIngestionCreditIT` | Email stored but not processed when credits = 0 |
| `ManualExecutionCreditIT` | Manual briefing execution returns 402 when credits = 0 |

### Test Data Setup

```java
// User with credits
User userWithCredits = createUserWithSubscription(SubscriptionPlan.FREE); // 900 credits

// User without credits
User userNoCredits = createUserWithSubscription(SubscriptionPlan.FREE);
userNoCredits.getSubscription().setCreditsBalance(0);
```

## Performance Considerations

### Batch Credit Checking

Instead of checking credits per-item in schedulers:

```java
// BAD: N+1 queries
for (NewsItem item : candidates) {
    UUID userId = item.getSource().getDayBrief().getUserId();
    if (subscriptionService.hasCredits(userId)) { // Query per item
        enqueue(item);
    }
}

// GOOD: Single query for all user IDs with credits
Set<UUID> usersWithCredits = subscriptionService.getUserIdsWithCredits(); // One query
for (NewsItem item : candidates) {
    UUID userId = item.getSource().getDayBrief().getUserId();
    if (usersWithCredits.contains(userId)) { // In-memory lookup
        enqueue(item);
    }
}
```

### Index Considerations

Existing indexes should suffice. The `subscriptions.credits_balance` column may benefit from a partial index:

```sql
CREATE INDEX idx_subscriptions_has_credits ON subscriptions(user_id) WHERE credits_balance > 0;
```

## Security Considerations

### Race Condition Prevention

Credit deduction uses optimistic locking via `@Version`:

```java
@Entity
public class Subscription {
    @Version
    private Long version; // Already exists in JPA
}
```

In `SubscriptionService.useCredits(UUID, int)`:
```java
@Transactional
public boolean useCredits(UUID userId, int credits) {
    Subscription sub = subscriptionRepository.findByUserId(userId)
            .orElseThrow();

    if (sub.getCreditsBalance() < credits) {
        return false;
    }

    sub.setCreditsBalance(sub.getCreditsBalance() - credits);
    subscriptionRepository.save(sub); // OptimisticLockException if concurrent update

    // Log usage
    creditUsageLogRepository.save(CreditUsageLog.builder()
            .user(sub.getUser())
            .subscription(sub)
            .creditsUsed(credits)
            .build());

    // Check if balance hit zero - send notification
    if (sub.getCreditsBalance() == 0) {
        sendNoCreditsNotification(sub.getUser());
    }

    return true;
}
```

### Input Validation

- Credit deduction only accepts positive values
- User ID validation via existing authentication

## Monitoring and Observability

### Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `credits.deducted.total` | Counter | Total credits deducted |
| `credits.deducted.user` | Counter (tagged by user_id) | Credits per user |
| `credits.exhausted.total` | Counter | Count of users hitting zero credits |
| `scheduler.skipped.no_credits` | Counter | Items/sources/briefings skipped |
| `ai.blocked.no_credits` | Counter | Safety net triggers |

### Logging

Structured log fields for credit operations:
```json
{
  "event": "credit_deducted",
  "userId": "uuid",
  "creditsDeducted": 1,
  "newBalance": 899,
  "newsItemId": "uuid"
}
```

### Alerts

| Alert | Condition | Severity |
|-------|-----------|----------|
| Safety net triggered | `ai.blocked.no_credits` > 0 | ERROR (indicates bug) |
| High credit exhaustion rate | `credits.exhausted.total` spike | WARN |

## Implementation Order

1. **Phase 1: Core Infrastructure**
   - Create `InsufficientCreditsException`
   - Extend `SubscriptionService` with new methods (hasCredits, getUserIdsWithCredits, useCredits overload)
   - Add new repository methods to `SubscriptionRepository`
   - Add handler to `GlobalExceptionHandler`

2. **Phase 2: Credit Deduction**
   - Modify `ProcessingWorker` to deduct credits
   - Add no-credits notification trigger

3. **Phase 3: Scheduler Blocking**
   - Modify `ProcessingSchedulerJob`
   - Modify `FeedSchedulerJob`
   - Modify `BriefingSchedulerJob`

4. **Phase 4: API and Listener Blocking**
   - Modify `DayBriefController.executeBriefing()`
   - Modify `EmailIngestionListener`

5. **Phase 5: Safety Net**
   - Modify `TrackedAiService`

6. **Phase 6: Email Reprocessing (Optional)**
   - Add `RawEmailStatus` enum and migration
   - Implement reprocessing job
