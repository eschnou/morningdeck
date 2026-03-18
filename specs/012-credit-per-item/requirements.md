# Credit-Per-Item: Requirements

## Introduction

This feature connects the existing credit management system to the news item processing pipeline. Users spend one credit per news item that undergoes AI processing (enrichment, scoring, summarization). When credits are exhausted, the system blocks:
- AI processing of news items (RSS, Web, Reddit sources)
- Email extraction and processing (newsletter sources)
- Daily briefing execution
- Source fetching (to prevent queue buildup)

Credits are replenished monthly based on the subscription plan.

## Alignment with Product Vision

From `specs/product.md`:
- **Monetization Model**: Tiered plans (Free: 900 credits/month, Pro: 10,800, Business: 36,000)
- **Intelligence Layer**: AI processing includes summarization, scoring, tagging - these consume computational resources
- **Daily Briefing**: Core feature that relies on processed news items

Credits serve as the metering mechanism for AI-powered features, enabling sustainable operation while giving users control over their usage.

## Current State Analysis

### Existing Credit Infrastructure
- **Subscription entity** with `creditsBalance`, `monthlyCredits`, `plan`
- **SubscriptionService.useCredits(User, int)** method ready but unused
- **CreditUsageLog** entity for tracking usage history
- **Auto-renewal scheduler** that resets credits monthly
- **Admin credit adjustment** functionality
- **EmailService.sendNoCreditsEmail()** method exists but is not called

### Identified Gaps

| Component | Gap Description |
|-----------|-----------------|
| **ProcessingWorker** | Calls `aiService.enrichWithScore()` without checking or deducting credits |
| **FetchWorker** | Creates NewsItems (status: NEW) without credit checks |
| **ProcessingSchedulerJob** | Schedules items without verifying user has credits |
| **FeedSchedulerJob** | Schedules source fetches without credit checks |
| **BriefingSchedulerJob** | Schedules briefings without credit checks |
| **BriefingWorker** | Executes briefings without credit validation |
| **DayBriefController.execute()** | Manual briefing execution without credit check |
| **EmailIngestionListener** | Calls `aiService.extractFromEmail()` without credit checks |
| **WebFetcher** | Calls `aiService.extractFromWeb()` without credit checks |
| **ReportEmailDeliveryService** | Calls `aiService.generateReportEmailContent()` without credit checks |
| **No-credits notification** | Email template exists but is never sent |

### AI Service Methods Inventory

All AI methods that consume LLM resources:

| Method | Called From | Credit Check Location |
|--------|-------------|----------------------|
| `enrichWithScore()` | ProcessingWorker | REQ-1, REQ-2 |
| `extractFromEmail()` | EmailIngestionListener | REQ-8 |
| `extractFromWeb()` | WebFetcher | REQ-4 (blocks source fetch) |
| `generateReportEmailContent()` | ReportEmailDeliveryService | REQ-3 (blocks briefing) |
| `enrich()` | Unused | N/A |
| `score()` | Unused | N/A |

## Requirements

### REQ-1: Deduct Credit on News Item Processing

**User Story**: As the system, I want to deduct one credit when a news item is AI-processed, so that credit consumption accurately reflects resource usage.

**Acceptance Criteria**:
- Credit is deducted in `ProcessingWorker.process()` after successful AI enrichment
- One credit per news item (regardless of content size or AI complexity)
- Credit deduction is logged in `CreditUsageLog`
- Processing transaction is atomic: either both AI processing succeeds and credit is deducted, or neither happens
- The user associated with the news item is determined via: `newsItem.source.dayBrief.userId`

### REQ-2: Block Processing When Zero Credits

**User Story**: As a user with zero credits, I expect the system to stop AI-processing my news items, so that I don't incur costs I cannot pay.

**Acceptance Criteria**:
- `ProcessingSchedulerJob` skips news items belonging to users with zero credits
- Items remain in `NEW` status when user has no credits (not marked as ERROR)
- When credits are replenished, items in `NEW` status will be picked up in the next scheduling cycle
- Log message indicates items were skipped due to insufficient credits

### REQ-3: Block Briefing Execution When Zero Credits

**User Story**: As a user with zero credits, I expect the system to skip my daily briefing execution, so that AI resources aren't consumed without payment.

**Acceptance Criteria**:
- `BriefingSchedulerJob` skips briefings belonging to users with zero credits
- Briefing remains in `ACTIVE` status (not scheduled, not marked as ERROR)
- Manual briefing execution via API also checks for credits and returns appropriate error
- When credits are replenished, briefing executes on the next scheduled cycle

**Note on Email Generation**: Briefing execution triggers `ReportEmailDeliveryService` which calls `aiService.generateReportEmailContent()`. Blocking at the scheduler level prevents this AI call from occurring.

### REQ-4: Block Source Fetching When Zero Credits

**User Story**: As a user with zero credits, I expect the system to stop fetching new items for my sources, so that items don't queue up indefinitely.

**Acceptance Criteria**:
- `FeedSchedulerJob` skips sources belonging to users with zero credits
- Sources remain in `ACTIVE` status with `fetchStatus=IDLE` (not marked as ERROR)
- When credits are replenished, sources resume fetching on the next scheduling cycle

**Note on WEB Sources**: WEB sources call `aiService.extractFromWeb()` during fetching (in `WebFetcher`). Blocking at the scheduler level prevents this AI call from occurring.

### REQ-5: No-Credits Notification Email

**User Story**: As a user who runs out of credits, I want to receive an email notification, so that I can take action to replenish my credits.

**Acceptance Criteria**:
- Email is sent when credit balance reaches zero
- Email is sent only once per credit exhaustion event (not on every blocked operation)
- Uses existing `sendNoCreditsEmail()` method
- Email includes link to subscription/upgrade page

### REQ-6: Credit Status in API Responses

**User Story**: As a user, I want to see my current credit balance in the application, so that I can monitor my usage.

**Acceptance Criteria**:
- Existing `GET /subscription` endpoint already returns `creditsBalance` (no change needed)
- Consider adding credit balance to DayBrief list response for visibility (optional)

### REQ-7: Preserve First-Import Skip Behavior

**User Story**: As a new user adding a source, I expect initial items to not consume credits, so that I don't exhaust credits on historical content.

**Acceptance Criteria**:
- First import behavior (`skipProcessing=true`, status=`DONE`) is preserved
- Only items marked `NEW` (subsequent fetches) consume credits
- This is existing behavior - no changes required, but must be preserved

### REQ-8: Block Email Extraction When Zero Credits

**User Story**: As a user with zero credits, I expect the system to not process incoming emails for news extraction, so that AI resources aren't consumed without payment.

**Acceptance Criteria**:
- `EmailIngestionListener.handleEmailReceived()` checks user credits before AI extraction
- If user has zero credits:
  - Raw email is stored in `RawEmail` table (for later processing)
  - AI extraction is skipped
  - No `NewsItem` entities are created
  - Log message indicates email was stored but not processed due to insufficient credits
- When credits are replenished, stored raw emails can be reprocessed (see REQ-9)

**Credit Model for Emails**:
- Email extraction AI call (`AiService.extractFromEmail`) does NOT directly cost credits
- Credits are charged per NewsItem during enrichment (same as RSS items - REQ-1)
- However, extraction is blocked when credits = 0 to prevent:
  1. Queueing items that cannot be processed
  2. Consuming AI resources without payment (extraction is still an AI call)
- This ensures all news items cost exactly 1 credit regardless of source type

### REQ-9: Reprocess Stored Raw Emails (Optional)

**User Story**: As a user who replenishes credits, I want my previously received emails to be processed, so that I don't lose newsletter content.

**Acceptance Criteria**:
- Mechanism to identify unprocessed `RawEmail` records (e.g., flag or status column)
- Scheduled job or manual trigger to reprocess stored emails
- Reprocessing follows the same credit deduction rules as new emails

**Note**: This requirement is optional for MVP. Alternative: emails received during zero-credit period are simply lost. Users are notified via the no-credits email (REQ-5) to replenish before newsletters arrive.

### REQ-10: AI Service Safety Net

**User Story**: As a developer, I want a fail-safe in the AI service layer that prevents AI calls when credits are zero, so that bugs in upstream code cannot bypass credit checks.

**Acceptance Criteria**:
- `TrackedAiService` (the decorator wrapping all AI calls) checks user credits before delegating to the actual AI implementation
- If `AiUsageContext.getUserId()` is set and that user has zero credits, throw `InsufficientCreditsException`
- If `AiUsageContext.getUserId()` is not set, allow the call (for admin/system operations)
- This is a **defense-in-depth** measure - primary credit checks happen at scheduler/listener level
- Exception should be caught and logged as ERROR (indicates a bug in upstream code)

**Rationale**: Even with proper blocking at scheduler levels, code changes or bugs could introduce paths that bypass credit checks. This safety net ensures no AI resources are consumed without payment, regardless of caller.

## Non-Functional Requirements

### Architecture
- Credit checks should be performed at the scheduler level (batch query) for efficiency, not per-item
- Use database queries to find users with credits > 0 rather than loading all items and filtering in Java
- The user-to-newsitem relationship chain is: `NewsItem -> Source -> DayBrief -> userId`

### Performance
- Credit balance checks should not significantly impact scheduler job performance
- Consider batch credit deduction if processing multiple items for the same user (optimization, not required for MVP)

### Security
- Credit deduction must be atomic to prevent race conditions
- Use database-level locking or optimistic locking to prevent double-spending

### Reliability
- If credit deduction fails, the news item should not be marked as processed
- System should gracefully handle edge cases (user deleted, subscription missing)

### Usability
- Clear error messages in API responses when operations fail due to insufficient credits
- Log messages should clearly indicate when operations are skipped due to zero credits

## Out of Scope

- Payment integration (Stripe, etc.) - credits are currently admin-managed
- Prorated credits for plan upgrades
- Credit purchase as add-on to subscription
- Rollover of unused credits
- Per-feature credit costs (all operations cost 1 credit)
