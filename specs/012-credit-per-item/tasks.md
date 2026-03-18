# Credit-Per-Item: Implementation Tasks

## Phase 1: Core Infrastructure

**Goal:** Create the exception type and extend SubscriptionService with credit-checking methods.

**Verification:** Unit tests pass for all new SubscriptionService methods.

### Task 1.1: Create InsufficientCreditsException

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/exception/InsufficientCreditsException.java`

```java
public class InsufficientCreditsException extends RuntimeException {
    private final UUID userId;
    private final int creditsRequired;
    private final int creditsAvailable;

    // Constructor with all fields
    // Getters
}
```

### Task 1.2: Add Exception Handler

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/exception/GlobalExceptionHandler.java`

Add handler returning `HttpStatus.PAYMENT_REQUIRED` (402).

**Test:** `GlobalExceptionHandlerTest` - verify 402 response for `InsufficientCreditsException`.

### Task 1.3: Add Repository Methods

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/repository/SubscriptionRepository.java`

Add:
```java
@Query("SELECT s.creditsBalance FROM Subscription s WHERE s.user.id = :userId")
Optional<Integer> findCreditsBalanceByUserId(@Param("userId") UUID userId);

@Query("SELECT s.user.id FROM Subscription s WHERE s.creditsBalance > 0")
Set<UUID> findUserIdsWithCredits();
```

**Test:** `SubscriptionRepositoryTest` - verify queries return expected results.

### Task 1.4: Extend SubscriptionService

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/service/SubscriptionService.java`

Add methods:
```java
public boolean hasCredits(UUID userId)
public int getCreditsBalance(UUID userId)
public Set<UUID> getUserIdsWithCredits()
@Transactional
public boolean useCredits(UUID userId, int credits)  // Overload with notification trigger
```

The `useCredits(UUID, int)` overload:
1. Fetches user by ID
2. Delegates to existing `useCredits(User, int)`
3. After successful deduction, checks if balance hit zero
4. If zero, calls `emailService.sendNoCreditsEmail()`

**Tests:**
- `SubscriptionServiceTest.hasCredits_returnsTrue_whenBalancePositive`
- `SubscriptionServiceTest.hasCredits_returnsFalse_whenBalanceZero`
- `SubscriptionServiceTest.useCredits_sendsNotification_whenBalanceHitsZero`
- `SubscriptionServiceTest.getUserIdsWithCredits_returnsOnlyUsersWithCredits`

---

## Phase 2: Credit Deduction in ProcessingWorker

**Goal:** Deduct one credit per news item after successful AI enrichment.

**Verification:** Process a news item → verify credit decremented and `CreditUsageLog` entry created.

### Task 2.1: Inject SubscriptionService into ProcessingWorker

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/queue/ProcessingWorker.java`

Add constructor parameter for `SubscriptionService`.

### Task 2.2: Deduct Credit After AI Processing

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/queue/ProcessingWorker.java`

In `doProcess()` method, after successful `aiService.enrichWithScore()` call:

```java
UUID userId = item.getSource().getDayBrief().getUserId();

// After AI call succeeds
boolean deducted = subscriptionService.useCredits(userId, 1);
if (!deducted) {
    log.error("Failed to deduct credit after AI processing: userId={}, itemId={}", userId, item.getId());
    throw new InsufficientCreditsException(userId, 1, 0);
}
```

**Tests:**
- `ProcessingWorkerTest.process_deductsOneCredit_afterSuccessfulEnrichment`
- `ProcessingWorkerTest.process_createsUsageLog_afterSuccessfulEnrichment`
- `ProcessingWorkerTest.process_throwsException_whenCreditDeductionFails`

---

## Phase 3: Scheduler Blocking

**Goal:** Schedulers skip items/sources/briefings for users with zero credits.

**Verification:** Create user with zero credits → verify their items/sources/briefings are not scheduled.

### Task 3.1: Modify ProcessingSchedulerJob

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/job/ProcessingSchedulerJob.java`

Inject `SubscriptionService`. In `scheduleProcessing()`:

```java
Set<UUID> usersWithCredits = subscriptionService.getUserIdsWithCredits();
if (usersWithCredits.isEmpty()) {
    log.debug("No users with credits available");
    return;
}

// Filter candidates to only include items from users with credits
List<NewsItem> affordableItems = candidates.stream()
    .filter(item -> usersWithCredits.contains(item.getSource().getDayBrief().getUserId()))
    .toList();
```

**Tests:**
- `ProcessingSchedulerJobTest.scheduleProcessing_skipsItems_forUsersWithZeroCredits`
- `ProcessingSchedulerJobTest.scheduleProcessing_processesItems_forUsersWithCredits`

### Task 3.2: Modify FeedSchedulerJob

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/job/FeedSchedulerJob.java`

Inject `SubscriptionService`. Filter `dueForRefresh` stream:

```java
Set<UUID> usersWithCredits = subscriptionService.getUserIdsWithCredits();

List<Source> dueForRefresh = candidates.stream()
    .filter(s -> isDueForRefresh(s, now))
    .filter(s -> usersWithCredits.contains(s.getDayBrief().getUserId()))
    .limit(batchSize)
    .toList();
```

Log when sources are skipped due to zero credits.

**Tests:**
- `FeedSchedulerJobTest.scheduleFeeds_skipsSources_forUsersWithZeroCredits`
- `FeedSchedulerJobTest.scheduleFeeds_processesSources_forUsersWithCredits`

### Task 3.3: Modify BriefingSchedulerJob

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/job/BriefingSchedulerJob.java`

Inject `SubscriptionService`. Filter `dueBriefings` stream:

```java
Set<UUID> usersWithCredits = subscriptionService.getUserIdsWithCredits();

List<DayBrief> dueBriefings = candidateBriefings.stream()
    .filter(briefing -> isBriefingDue(briefing, nowUtc))
    .filter(briefing -> usersWithCredits.contains(briefing.getUserId()))
    .toList();
```

Log when briefings are skipped due to zero credits.

**Tests:**
- `BriefingSchedulerJobTest.scheduleBriefings_skipsBriefings_forUsersWithZeroCredits`
- `BriefingSchedulerJobTest.scheduleBriefings_processesBriefings_forUsersWithCredits`

---

## Phase 4: API and Listener Blocking

**Goal:** Block manual briefing execution and email extraction when zero credits.

**Verification:** Call manual execute endpoint with zero credits → receive 402. Send email to user with zero credits → email stored but not processed.

### Task 4.1: Block Manual Briefing Execution

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/controller/DayBriefController.java`

In `executeBriefing()` method, before `briefingWorker.executeManually()`:

```java
if (!subscriptionService.hasCredits(user.getId())) {
    throw new InsufficientCreditsException(user.getId(), 1, 0);
}
```

**Tests:**
- `DayBriefControllerTest.executeBriefing_returns402_whenZeroCredits`
- `DayBriefControllerTest.executeBriefing_succeeds_whenCreditsAvailable`

### Task 4.2: Block Email Extraction

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/service/EmailIngestionListener.java`

Before `aiService.extractFromEmail()`:

```java
UUID userId = source.getDayBrief().getUserId();
if (!subscriptionService.hasCredits(userId)) {
    log.warn("Email received but user {} has no credits. Stored but not processed: messageId={}",
            userId, email.getMessageId());
    // RawEmail already saved earlier - just don't process
    return;
}
```

**Tests:**
- `EmailIngestionListenerTest.handleEmailReceived_skipsProcessing_whenZeroCredits`
- `EmailIngestionListenerTest.handleEmailReceived_storesRawEmail_evenWhenZeroCredits`
- `EmailIngestionListenerTest.handleEmailReceived_processesEmail_whenCreditsAvailable`

---

## Phase 5: Safety Net in TrackedAiService

**Goal:** Defense-in-depth: throw exception if AI call attempted with zero credits.

**Verification:** Trigger safety net → ERROR log and exception thrown.

### Task 5.1: Add Credit Check in TrackedAiService

**File:** `backend/src/main/java/be/transcode/morningdeck/server/provider/ai/TrackedAiService.java`

Inject `SubscriptionService`. At start of `trackCall()`:

```java
UUID userId = AiUsageContext.getUserId();
if (userId != null) {
    int balance = subscriptionService.getCreditsBalance(userId);
    if (balance <= 0) {
        log.error("SAFETY NET: AI call attempted with zero credits: userId={} feature={}", userId, feature);
        throw new InsufficientCreditsException(userId, 1, balance);
    }
}
```

**Tests:**
- `TrackedAiServiceTest.trackCall_throwsException_whenZeroCredits`
- `TrackedAiServiceTest.trackCall_proceeds_whenCreditsAvailable`
- `TrackedAiServiceTest.trackCall_proceeds_whenNoUserContext` (system/admin operations)

---

## Phase 6: Integration Tests

**Goal:** End-to-end verification of credit enforcement.

**Verification:** All integration tests pass.

### Task 6.1: Credit Deduction Integration Test

**File:** `backend/src/test/java/be/transcode/morningdeck/server/core/CreditEnforcementIT.java`

Test scenarios:
- Create user with 5 credits
- Process 5 news items
- Verify credit balance is 0
- Verify 5 `CreditUsageLog` entries
- Verify no-credits notification email sent

### Task 6.2: Scheduler Credit Filtering Integration Test

**File:** Same as 6.1

Test scenarios:
- Create two users: one with credits, one without
- Create news items for both users
- Run ProcessingSchedulerJob
- Verify only items for user with credits are scheduled

### Task 6.3: API Blocking Integration Test

**File:** Same as 6.1

Test scenarios:
- Create user with zero credits
- Call `POST /daybriefs/{id}/execute`
- Verify 402 Payment Required response

---

## Phase 7: Documentation Update

**Goal:** Document credit system behavior.

**Verification:** Documentation reflects implemented behavior.

### Task 7.1: Update Documentation

**File:** `documentation/credits.md` (create if not exists)

Document:
- Credit costs per operation (1 credit per processed item)
- What happens when credits reach zero
- How credits are replenished (monthly auto-renewal)
- No-credits notification behavior

### Task 7.2: Update API Documentation

Ensure OpenAPI/Swagger reflects 402 responses on relevant endpoints.
