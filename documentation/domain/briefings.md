# Briefings

A Briefing (DayBrief) is a user-defined collection of sources with scheduling configuration that generates daily or weekly reports containing the top-scored news items.

## Overview

Briefings are the central organizing concept in Morning Deck:
- Users create briefings with criteria describing what content is relevant to them
- Each briefing contains one or more sources (RSS, Web, Email, Reddit)
- News items from sources are scored against the briefing criteria
- At scheduled times, the system generates a report with the top 10 scored items
- Reports can be delivered via email

## Data Model

### DayBrief Entity

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/model/DayBrief.java`

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | Primary key |
| `userId` | UUID | Owner of the briefing |
| `title` | String | Display name |
| `description` | String(1024) | Optional description |
| `briefing` | TEXT | Criteria text used for AI scoring |
| `frequency` | BriefingFrequency | DAILY or WEEKLY |
| `scheduleDayOfWeek` | DayOfWeek | For WEEKLY: which day to run |
| `scheduleTime` | LocalTime | Time of day to execute |
| `timezone` | String | User's timezone (default: UTC) |
| `status` | DayBriefStatus | Current status (see below) |
| `lastExecutedAt` | Instant | When last report was generated |
| `queuedAt` | Instant | When queued for execution |
| `processingStartedAt` | Instant | When worker started processing |
| `errorMessage` | String(1024) | Error details if failed |
| `emailDeliveryEnabled` | boolean | Whether to send email reports |
| `position` | Integer | Display order in sidebar |
| `sources` | List&lt;Source&gt; | Linked sources (one-to-many) |

### Status Lifecycle

```
┌──────────────────────────────────────────────────────────────────────┐
│                                                                       │
│   ┌────────┐     ┌────────┐     ┌────────────┐     ┌────────┐       │
│   │ ACTIVE │────►│ QUEUED │────►│ PROCESSING │────►│ ACTIVE │       │
│   └────────┘     └────────┘     └────────────┘     └────────┘       │
│       │                               │                              │
│       │                               │                              │
│       ▼                               ▼                              │
│   ┌────────┐                     ┌────────┐                         │
│   │ PAUSED │                     │ ERROR  │                         │
│   └────────┘                     └────────┘                         │
│                                                                       │
└──────────────────────────────────────────────────────────────────────┘
```

| Status | Description |
|--------|-------------|
| `ACTIVE` | Enabled and idle, ready to be scheduled |
| `QUEUED` | In queue, waiting for worker |
| `PROCESSING` | Worker is currently executing this briefing |
| `ERROR` | Failed during last processing attempt |
| `PAUSED` | Disabled by user (won't be scheduled) |

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/model/DayBriefStatus.java`

### BriefingFrequency

| Value | Behavior |
|-------|----------|
| `DAILY` | Runs every day at `scheduleTime` |
| `WEEKLY` | Runs on `scheduleDayOfWeek` at `scheduleTime` |

## Reports

When a briefing executes, it generates a `DailyReport` containing the top-scored items.

### DailyReport Entity

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/model/DailyReport.java`

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | Primary key |
| `dayBrief` | DayBrief | Parent briefing |
| `generatedAt` | Instant | When report was created |
| `status` | ReportStatus | PENDING or GENERATED |
| `items` | List&lt;ReportItem&gt; | News items in the report |

### ReportItem Entity

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/model/ReportItem.java`

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | Primary key |
| `report` | DailyReport | Parent report |
| `newsItem` | NewsItem | The included news item |
| `score` | Integer | Score at time of report generation |
| `position` | Integer | Order in the report (1-based) |

## Execution Pipeline

### Scheduling

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/job/BriefingSchedulerJob.java`

The scheduler job runs every 60 seconds (configurable) and:

1. Finds ACTIVE briefings not yet executed today (in UTC)
2. For each candidate:
   - Converts current UTC time to briefing's timezone
   - Checks if `scheduleTime` has passed
   - For WEEKLY: also checks if today matches `scheduleDayOfWeek`
3. Filters out users without available credits
4. Marks eligible briefings as `QUEUED`
5. Enqueues to `BriefingQueue`

```java
// Timezone-aware scheduling (BriefingSchedulerJob.java:110-133)
boolean isBriefingDue(DayBrief briefing, Instant nowUtc) {
    ZoneId userZone = ZoneId.of(briefing.getTimezone());
    ZonedDateTime userNow = nowUtc.atZone(userZone);
    LocalTime userCurrentTime = userNow.toLocalTime();

    // Check day-of-week for WEEKLY briefs
    if (briefing.getFrequency() == BriefingFrequency.WEEKLY) {
        if (briefing.getScheduleDayOfWeek() != userNow.getDayOfWeek()) {
            return false;
        }
    }

    return !briefing.getScheduleTime().isAfter(userCurrentTime);
}
```

### Processing

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/queue/BriefingWorker.java`

The worker processes queued briefings:

1. Transitions status: `QUEUED` → `PROCESSING`
2. Gets source IDs from the briefing
3. Queries top 10 scored items (`NewsItemStatus.DONE`) since `lastExecutedAt`
   - First run lookback: 7 days for WEEKLY, 1 day for DAILY
4. Creates `DailyReport` with `ReportItem` entries
5. Updates `lastExecutedAt` timestamp
6. Transitions status: `PROCESSING` → `ACTIVE` (or `ERROR` on failure)
7. Sends email via `ReportEmailDeliveryService` if enabled

**Key constant:** `MAX_REPORT_ITEMS = 10` (line 35)

### Email Delivery

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/service/ReportEmailDeliveryService.java`

When `emailDeliveryEnabled` is true:
1. Formats report items for AI prompt
2. Calls `AiService.generateReportEmailContent()` to generate subject and summary
3. Sends formatted email via `EmailService.sendDailyReportEmail()`

Email delivery failures don't fail the report generation (graceful degradation).

## Manual Execution

Users can manually trigger a briefing via the API:

```
POST /daybriefs/{id}/execute
```

This:
- Bypasses the scheduler queue
- Executes synchronously in the request
- Returns the generated report
- Requires user to have available credits (returns HTTP 402 otherwise)

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/controller/DayBriefController.java`

## Brief Ordering

Users can reorder briefs in the sidebar via drag-and-drop.

### API

```
POST /daybriefs/reorder
{
  "briefIds": ["uuid1", "uuid2", "uuid3"]
}
```

- Request must include ALL user's brief IDs in desired order
- Returns 204 No Content on success
- Returns 400 Bad Request if IDs are missing or invalid

### Frontend Implementation

- Uses `@dnd-kit` library for drag-and-drop
- `SortableBriefItem` component wraps each brief
- `SidebarBriefsList` handles DnD context and reorder API calls
- Optimistic UI updates with rollback on failure
- Touch and keyboard support

### Position Assignment

- New briefs get `position = MAX(position) + 1`
- Reorder sets positions to match array index (0, 1, 2, ...)

## Configuration

```properties
# Enable/disable the briefing scheduler job
application.jobs.briefing-execution.enabled=true

# How often to check for due briefings (milliseconds)
application.jobs.briefing-execution.interval=60000
```

## Key Files Reference

| Component | File Path |
|-----------|-----------|
| DayBrief Entity | `backend/.../core/model/DayBrief.java` |
| DailyReport Entity | `backend/.../core/model/DailyReport.java` |
| ReportItem Entity | `backend/.../core/model/ReportItem.java` |
| DayBriefStatus Enum | `backend/.../core/model/DayBriefStatus.java` |
| BriefingFrequency Enum | `backend/.../core/model/BriefingFrequency.java` |
| BriefingWorker | `backend/.../core/queue/BriefingWorker.java` |
| BriefingSchedulerJob | `backend/.../core/job/BriefingSchedulerJob.java` |
| DayBriefService | `backend/.../core/service/DayBriefService.java` |
| ReportService | `backend/.../core/service/ReportService.java` |
| ReportEmailDeliveryService | `backend/.../core/service/ReportEmailDeliveryService.java` |
| DayBriefController | `backend/.../core/controller/DayBriefController.java` |
| DayBriefRepository | `backend/.../core/repository/DayBriefRepository.java` |

## Related Documentation

- [Sources](./sources.md) - How content enters briefings
- [News Items](./news-items.md) - Item scoring and enrichment
- [Credits](./credits.md) - Credit requirements for execution
- [Queue System](../architecture/queue-system.md) - BriefingQueue details
- [Email Infrastructure](../architecture/email-infrastructure.md) - Report delivery
