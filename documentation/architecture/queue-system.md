# Queue System

Morning Deck uses an in-memory queue architecture for background processing. Three independent queues handle source fetching, AI enrichment, and briefing execution.

## Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Scheduler Jobs (Every 60s)                        │
├─────────────────┬─────────────────────┬─────────────────────────────────────┤
│ FeedScheduler   │ ProcessingScheduler │ BriefingScheduler                   │
│ (sources)       │ (news items)        │ (briefings)                         │
└────────┬────────┴──────────┬──────────┴──────────────┬──────────────────────┘
         │                   │                         │
         │ enqueue()         │ enqueue()               │ enqueue()
         ▼                   ▼                         ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────────────────────────┐
│   FetchQueue    │ │ ProcessingQueue │ │           BriefingQueue             │
│ (BlockingQueue) │ │ (BlockingQueue) │ │         (BlockingQueue)             │
└────────┬────────┘ └────────┬────────┘ └──────────────┬──────────────────────┘
         │                   │                         │
         │ poll()            │ poll()                  │ poll()
         ▼                   ▼                         ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────────────────────────┐
│  FetchWorker    │ │ProcessingWorker │ │         BriefingWorker              │
│ (N threads)     │ │ (N threads)     │ │         (N threads)                 │
└─────────────────┘ └─────────────────┘ └─────────────────────────────────────┘
```

## Queue Interface

All queues share the same interface pattern:

```java
public interface FetchQueue {
    boolean enqueue(UUID id);    // Add item to queue
    boolean canAccept();         // Check for backpressure
    int size();                  // Current queue depth
}
```

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/queue/FetchQueue.java`

## In-Memory Implementation

### Architecture

Each queue uses:
- `LinkedBlockingQueue<UUID>` with configurable capacity
- `ExecutorService` with configurable worker thread pool
- Worker loop: poll queue → process → repeat

```java
// InMemoryFetchQueue.java:31-42
public InMemoryFetchQueue(
        FetchWorker fetchWorker,
        @Value("${application.jobs.feed-ingestion.queue-capacity:1000}") int capacity,
        @Value("${application.jobs.feed-ingestion.worker-count:4}") int workerCount) {
    this.queue = new LinkedBlockingQueue<>(capacity);
    this.fetchWorker = fetchWorker;
    this.executor = Executors.newFixedThreadPool(workerCount);
    startWorkers();
}
```

### Worker Loop

```java
// InMemoryFetchQueue.java:52-75
private void workerLoop(int workerId) {
    while (running && !Thread.currentThread().isInterrupted()) {
        UUID id = queue.poll(1, TimeUnit.SECONDS);
        if (id != null) {
            try {
                worker.process(id);
            } catch (Exception e) {
                log.error("Worker {} failed: {}", workerId, e.getMessage());
            }
        }
    }
}
```

### Graceful Shutdown

Queues implement `@PreDestroy` for clean shutdown:
- Set `running = false` to stop worker loops
- Wait up to 30 seconds for in-progress work
- Force shutdown if timeout exceeded

## The Three Queues

### 1. FetchQueue (Source Fetching)

**Purpose:** Fetch content from sources (RSS, Web, Reddit)

**Scheduler:** `FeedSchedulerJob`
- Runs every 60 seconds
- Finds sources due for refresh based on per-source `refreshIntervalMinutes`
- Filters by user credit availability
- Marks source as `QUEUED`, enqueues to FetchQueue

**Worker:** `FetchWorker`
1. Marks source `FETCHING`
2. Calls `SourceFetcher.fetch()` for the source type
3. Creates `NewsItem` entities for new items
4. First import: marks items as `DONE` (skips AI processing)
5. Subsequent: marks items as `NEW` (queues for processing)
6. Marks source `IDLE` on success, `ERROR` on failure

**Status Flow:**
```
Source.fetchStatus: IDLE → QUEUED → FETCHING → IDLE
```

**Configuration:**
```properties
application.jobs.feed-ingestion.enabled=true
application.jobs.feed-ingestion.queue-capacity=1000
application.jobs.feed-ingestion.worker-count=4
application.jobs.feed-scheduling.interval=60000
application.jobs.feed-ingestion.batch-size=100
```

### 2. ProcessingQueue (AI Enrichment)

**Purpose:** Enrich news items with AI (summary, tags, score)

**Scheduler:** `ProcessingSchedulerJob`
- Runs every 60 seconds
- Finds `NewsItem` with status `NEW`
- Filters by user credit availability
- Marks item as `PENDING`, enqueues to ProcessingQueue

**Worker:** `ProcessingWorker`
1. Marks item `PROCESSING`
2. Optionally fetches web content (if existing content < 2000 chars)
3. Calls `AiService.enrichWithScore()`
4. Stores summary, tags, score, scoreReasoning
5. Deducts 1 credit
6. Marks item `DONE` (or `ERROR`)
7. Indexes in Meilisearch (if enabled)

**Status Flow:**
```
NewsItem.status: NEW → PENDING → PROCESSING → DONE
```

**Configuration:**
```properties
application.jobs.processing.enabled=true
application.jobs.processing.queue-capacity=1000
application.jobs.processing.worker-count=4
application.jobs.processing.interval=60000
application.jobs.processing.batch-size=50
```

### 3. BriefingQueue (Report Generation)

**Purpose:** Execute briefings and generate reports

**Scheduler:** `BriefingSchedulerJob`
- Runs every 60 seconds
- Finds `ACTIVE` briefings not executed today
- Timezone-aware: checks if scheduled time has passed in user's timezone
- For WEEKLY: also checks day of week
- Filters by user credit availability
- Marks briefing as `QUEUED`, enqueues to BriefingQueue

**Worker:** `BriefingWorker`
1. Marks briefing `PROCESSING`
2. Gets top 10 scored items (`DONE` status) since last execution
3. Creates `DailyReport` with `ReportItem` entries
4. Updates `lastExecutedAt`
5. Sends email via `ReportEmailDeliveryService` (if enabled)
6. Marks briefing `ACTIVE` (or `ERROR`)

**Status Flow:**
```
DayBrief.status: ACTIVE → QUEUED → PROCESSING → ACTIVE
```

**Configuration:**
```properties
application.jobs.briefing-execution.enabled=true
application.jobs.briefing-execution.queue-capacity=100
application.jobs.briefing-execution.worker-count=2
application.jobs.briefing-execution.interval=60000
```

## Recovery Jobs

Recovery jobs handle items stuck in transitional states (after crashes or failures).

### Pattern

Each recovery job:
- Runs every 5 minutes
- Finds items in QUEUED/PROCESSING state beyond threshold
- Resets to initial state for re-processing

### StuckSourceRecoveryJob

**File:** `backend/src/main/java/be/transcode/morningdeck/server/core/job/StuckSourceRecoveryJob.java`

- Resets sources stuck in `QUEUED` or `FETCHING` for > 10 minutes
- Sets `fetchStatus = IDLE` to allow rescheduling

### StuckNewsItemRecoveryJob

- Resets items stuck in `PENDING` or `PROCESSING` for > 10 minutes
- Sets `status = NEW` to allow reprocessing

### StuckBriefingRecoveryJob

- Resets briefings stuck in `QUEUED` or `PROCESSING` for > 10 minutes
- Sets `status = ACTIVE` to allow rescheduling

**Configuration:**
```properties
application.jobs.feed-recovery.interval=300000
application.jobs.feed-ingestion.stuck-threshold-minutes=10
```

## Backpressure

The system prevents queue overflow:

1. **Scheduler checks:** `if (!queue.canAccept()) return;`
2. **Per-item check:** `if (!queue.canAccept()) break;`
3. **Queue offers:** `queue.offer(id)` returns false if full

When queue is full:
- Scheduler logs warning and skips cycle
- Items remain in their current state
- Next cycle will attempt to enqueue again

## Status Transitions

### Why Separate Transactions?

Status updates use separate transactions for immediate visibility:

```java
// DayBriefService.java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void updateStatus(UUID id, DayBriefStatus status) {
    dayBriefRepository.updateStatus(id, status);
}
```

This ensures:
- Other threads see the status change immediately
- Recovery jobs can find stuck items
- UI shows accurate status

## Design Rationale

### Why In-Memory?

- **Simplicity:** Single process, no external dependencies
- **Sufficient scale:** Handles expected load (hundreds of items/minute)
- **Acceptable trade-off:** Work lost on restart is recoverable (recovery jobs)

### Why Not Redis/SQS?

- Adds deployment complexity
- Requires additional infrastructure
- Overkill for current scale
- Interface abstraction allows future migration if needed

## Key Files Reference

| Component | File Path |
|-----------|-----------|
| FetchQueue Interface | `backend/.../core/queue/FetchQueue.java` |
| ProcessingQueue Interface | `backend/.../core/queue/ProcessingQueue.java` |
| BriefingQueue Interface | `backend/.../core/queue/BriefingQueue.java` |
| InMemoryFetchQueue | `backend/.../core/queue/InMemoryFetchQueue.java` |
| InMemoryProcessingQueue | `backend/.../core/queue/InMemoryProcessingQueue.java` |
| InMemoryBriefingQueue | `backend/.../core/queue/InMemoryBriefingQueue.java` |
| FetchWorker | `backend/.../core/queue/FetchWorker.java` |
| ProcessingWorker | `backend/.../core/queue/ProcessingWorker.java` |
| BriefingWorker | `backend/.../core/queue/BriefingWorker.java` |
| FeedSchedulerJob | `backend/.../core/job/FeedSchedulerJob.java` |
| ProcessingSchedulerJob | `backend/.../core/job/ProcessingSchedulerJob.java` |
| BriefingSchedulerJob | `backend/.../core/job/BriefingSchedulerJob.java` |
| StuckSourceRecoveryJob | `backend/.../core/job/StuckSourceRecoveryJob.java` |
| StuckNewsItemRecoveryJob | `backend/.../core/job/StuckNewsItemRecoveryJob.java` |
| StuckBriefingRecoveryJob | `backend/.../core/job/StuckBriefingRecoveryJob.java` |

## Related Documentation

- [Sources](../domain/sources.md) - FetchQueue details
- [News Items](../domain/news-items.md) - ProcessingQueue details
- [Briefings](../domain/briefings.md) - BriefingQueue details
- [Configuration](../operations/configuration.md) - Queue configuration options
