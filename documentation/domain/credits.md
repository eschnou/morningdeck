# Credit System

The credit system controls access to AI-powered features by requiring users to have available credits before processing news items.

## Credit Cost

| Operation | Credit Cost |
|-----------|-------------|
| AI enrichment (per news item) | 1 credit |

## Subscription Plans

| Plan | Monthly Credits | Target Use Case |
|------|-----------------|-----------------|
| FREE | 1,000 | ~33 items/day |
| PRO | 10,000 | ~333 items/day |
| BUSINESS | 50,000 | ~1,667 items/day |

Credits reset monthly on the subscription renewal date.

## Credit Lifecycle

### Allocation
- New users receive credits based on their plan
- Credits reset to `monthly_credits` on `next_renewal_date`
- Auto-renewal can be enabled/disabled per subscription

### Consumption
- 1 credit deducted per news item after successful AI enrichment
- Credits are deducted in `ProcessingWorker` after AI call succeeds
- A `CreditUsageLog` entry is created for each deduction

### Exhaustion
- When balance hits zero, a "no-credits" email is sent
- All processing stops until credits are renewed or plan upgraded

## Credit Enforcement Points

The system enforces credits at multiple levels (defense in depth):

### 1. Scheduler Level (Batch Filtering)

All three schedulers filter out users with zero credits before scheduling work:

- **ProcessingSchedulerJob**: Skips `NEW` status items for users with zero credits
- **FeedSchedulerJob**: Skips feed fetching for users with zero credits
- **BriefingSchedulerJob**: Skips briefing execution for users with zero credits

This prevents unnecessary work from being queued.

### 2. Worker Level (Credit Deduction)

**ProcessingWorker** deducts 1 credit after successful AI enrichment:
- Credit is deducted only after the AI call succeeds
- If deduction fails (edge case), the item is marked as ERROR
- Creates a `CreditUsageLog` entry for each deduction

### 3. API Level (Manual Execution)

**DayBriefController** `/daybriefs/{id}/execute` endpoint:
- Returns HTTP 402 (Payment Required) if user has zero credits
- Prevents manual briefing execution without credits

### 4. Email Ingestion Level

**EmailIngestionListener**:
- Raw email is always stored for audit (regardless of credits)
- AI extraction is skipped if user has zero credits
- Email remains unprocessed until user regains credits

### 5. Safety Net (Defense in Depth)

**TrackedAiService** includes a safety net check:
- Logs `SAFETY NET` error if AI call attempted with zero credits
- Throws `InsufficientCreditsException` to prevent AI usage
- Should never trigger if upstream checks work correctly

## API Response

When credits are insufficient, the API returns:

```json
HTTP 402 Payment Required
{
  "status": 402,
  "message": "Insufficient credits for user {userId}: required=1, available=0",
  "timestamp": "2024-01-15T10:00:00Z"
}
```

## Database Schema

### credit_usage_logs

Tracks every credit deduction:

| Column | Type | Description |
|--------|------|-------------|
| id | UUID | Primary key |
| user_id | UUID | User who was charged |
| subscription_id | UUID | Associated subscription |
| credits_used | INTEGER | Number of credits deducted |
| used_at | TIMESTAMP | When the deduction occurred |

### subscriptions

Tracks user subscription and balance:

| Column | Type | Description |
|--------|------|-------------|
| id | UUID | Primary key |
| user_id | UUID | Foreign key to user |
| plan | Enum | FREE, PRO, BUSINESS |
| credits_balance | INTEGER | Current available credits |
| monthly_credits | INTEGER | Credits allocated per month |
| next_renewal_date | TIMESTAMP | When credits will reset |
| auto_renew | BOOLEAN | Whether to auto-renew monthly |

## Subscription Service

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/service/SubscriptionService.java`

Key methods:

| Method | Purpose |
|--------|---------|
| `hasCredits(userId)` | Check if user has any credits |
| `useCredits(userId, amount)` | Deduct credits and log usage |
| `getCreditsBalance(userId)` | Get current balance |
| `getUserIdsWithCredits()` | Batch query for scheduler filtering |
| `checkAndRenewSubscriptions()` | Scheduled renewal (hourly) |
| `upgradePlan(userId, plan)` | Change plan and reset credits |

## Key Files

| File | Purpose |
|------|---------|
| `SubscriptionService.java` | Credit checking and deduction logic |
| `SubscriptionRepository.java` | Credit balance queries |
| `InsufficientCreditsException.java` | Custom exception for zero credits |
| `GlobalExceptionHandler.java` | Maps exception to HTTP 402 |
| `ProcessingWorker.java` | Deducts credits after AI processing |
| `ProcessingSchedulerJob.java` | Filters items by credit availability |
| `FeedSchedulerJob.java` | Filters sources by credit availability |
| `BriefingSchedulerJob.java` | Filters briefings by credit availability |
| `DayBriefController.java` | Blocks manual execution without credits |
| `EmailIngestionListener.java` | Blocks email processing without credits |
| `TrackedAiService.java` | Safety net credit check |

## Related Documentation

- [Users](./users.md) - User and subscription management
- [AI Integration](../architecture/ai-integration.md) - What consumes credits
- [Queue System](../architecture/queue-system.md) - Where credits are enforced
