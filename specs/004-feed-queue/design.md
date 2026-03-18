# Feed Queue System - Design Document

## 1. Overview

This document describes the redesign of the feed ingestion system from a simple scheduled job to a queue-based architecture. The new design provides:

- **Immediate pickup** of newly created sources (no waiting for next scheduled interval)
- **Per-source refresh intervals** (configurable polling frequency)
- **No duplicate processing** (in-progress tracking prevents overlapping runs)
- **Future-proof architecture** (easy migration to AWS SQS or other external queues)

## 2. Architecture

### 2.1 Current State (Before)

```
┌─────────────────────────────────────────────────────────────┐
│  FeedIngestionJob (@Scheduled every 15 min)                 │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ for each ACTIVE source:                              │    │
│  │   fetch(source)                                      │    │
│  │   save news items                                    │    │
│  └─────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

Problems:
- New sources wait up to 15 minutes for first fetch
- All sources polled at same interval regardless of update frequency
- No protection against duplicate processing if job runs long

### 2.2 Target State (After)

```
┌─────────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  FeedSchedulerJob   │     │   FetchQueue     │     │  FetchWorker    │
│  (every 1 minute)   │────▶│   (interface)    │────▶│  (thread pool)  │
└─────────────────────┘     └──────────────────┘     └─────────────────┘
         │                          │                        │
         │ SELECT sources           │ enqueue(sourceId)      │ process(sourceId)
         │ WHERE fetchStatus=IDLE   │                        │   fetch RSS
         │ AND needsRefresh()       │                        │   save items
         │                          │                        │   update status
         └── UPDATE fetchStatus ────┴────────────────────────┘
             = QUEUED
```

## 3. Data Model Changes

### 3.1 Source Entity Extensions

```java
// Add to Source entity
@Enumerated(EnumType.STRING)
@Column(name = "fetch_status", nullable = false)
@Builder.Default
private FetchStatus fetchStatus = FetchStatus.IDLE;

@Column(name = "refresh_interval_minutes", nullable = false)
@Builder.Default
private Integer refreshIntervalMinutes = 15;

@Column(name = "queued_at")
private LocalDateTime queuedAt;

@Column(name = "fetch_started_at")
private LocalDateTime fetchStartedAt;
```

### 3.2 FetchStatus Enum

```java
public enum FetchStatus {
    IDLE,       // Ready to be scheduled
    QUEUED,     // In queue, waiting for worker
    FETCHING    // Worker is actively fetching
}
```

### 3.3 Database Migration

```sql
-- V5__Feed_queue_support.sql

ALTER TABLE sources
ADD COLUMN fetch_status VARCHAR(50) NOT NULL DEFAULT 'IDLE';

ALTER TABLE sources
ADD COLUMN refresh_interval_minutes INTEGER NOT NULL DEFAULT 15;

ALTER TABLE sources
ADD COLUMN queued_at TIMESTAMP;

ALTER TABLE sources
ADD COLUMN fetch_started_at TIMESTAMP;

-- Index for scheduler query
CREATE INDEX idx_sources_fetch_scheduling
ON sources(fetch_status, last_fetched_at, refresh_interval_minutes)
WHERE status = 'ACTIVE';
```

## 4. Component Design

### 4.1 FetchQueue Interface

The queue abstraction enables swapping implementations without changing business logic.

```java
// core/queue/FetchQueue.java
public interface FetchQueue {
    /**
     * Add a source to the fetch queue.
     * @param sourceId The source to fetch
     */
    void enqueue(Long sourceId);

    /**
     * Check if the queue is accepting new items.
     * Used for backpressure when queue is full.
     */
    boolean canAccept();

    /**
     * Get current queue depth (for monitoring).
     */
    int size();
}
```

### 4.2 InMemoryFetchQueue Implementation

```java
// core/queue/InMemoryFetchQueue.java
@Component
@Slf4j
public class InMemoryFetchQueue implements FetchQueue {

    private final BlockingQueue<Long> queue;
    private final ExecutorService executor;
    private final FetchWorker fetchWorker;
    private final int workerCount;

    public InMemoryFetchQueue(
            FetchWorker fetchWorker,
            @Value("${application.jobs.feed-ingestion.queue-capacity:1000}") int capacity,
            @Value("${application.jobs.feed-ingestion.worker-count:4}") int workerCount) {
        this.queue = new LinkedBlockingQueue<>(capacity);
        this.fetchWorker = fetchWorker;
        this.workerCount = workerCount;
        this.executor = Executors.newFixedThreadPool(workerCount);
        startWorkers();
    }

    private void startWorkers() {
        for (int i = 0; i < workerCount; i++) {
            executor.submit(this::workerLoop);
        }
    }

    private void workerLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Long sourceId = queue.poll(1, TimeUnit.SECONDS);
                if (sourceId != null) {
                    fetchWorker.process(sourceId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Worker error", e);
            }
        }
    }

    @Override
    public void enqueue(Long sourceId) {
        if (!queue.offer(sourceId)) {
            log.warn("Queue full, dropping source_id={}", sourceId);
            // Could implement dead-letter here
        }
    }

    @Override
    public boolean canAccept() {
        return queue.remainingCapacity() > 0;
    }

    @Override
    public int size() {
        return queue.size();
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}
```

### 4.3 FetchWorker

The worker processes individual sources. Separated from queue for testability.

```java
// core/queue/FetchWorker.java
@Component
@RequiredArgsConstructor
@Slf4j
public class FetchWorker {

    private final SourceRepository sourceRepository;
    private final NewsItemRepository newsItemRepository;
    private final List<SourceFetcher> sourceFetchers;

    @Transactional
    public void process(Long sourceId) {
        Source source = sourceRepository.findById(sourceId).orElse(null);
        if (source == null) {
            log.warn("Source not found: {}", sourceId);
            return;
        }

        // Mark as fetching
        source.setFetchStatus(FetchStatus.FETCHING);
        source.setFetchStartedAt(LocalDateTime.now());
        sourceRepository.save(source);

        try {
            doFetch(source);

            // Success: reset to idle
            source.setFetchStatus(FetchStatus.IDLE);
            source.setLastFetchedAt(LocalDateTime.now());
            source.setLastError(null);
            source.setQueuedAt(null);
            source.setFetchStartedAt(null);

        } catch (Exception e) {
            log.error("Failed to fetch source {}: {}", sourceId, e.getMessage());

            // Error: reset to idle with error message
            source.setFetchStatus(FetchStatus.IDLE);
            source.setStatus(SourceStatus.ERROR);
            source.setLastError(e.getMessage());
            source.setQueuedAt(null);
            source.setFetchStartedAt(null);
        }

        sourceRepository.save(source);
    }

    private void doFetch(Source source) {
        SourceFetcher fetcher = findFetcher(source.getType());
        List<FetchedItem> items = fetcher.fetch(source, source.getLastFetchedAt());

        for (FetchedItem item : items) {
            if (!newsItemRepository.existsBySourceIdAndGuid(source.getId(), item.getGuid())) {
                NewsItem newsItem = mapToNewsItem(source, item);
                newsItemRepository.save(newsItem);
            }
        }

        log.info("Fetched source_id={} items_count={}", source.getId(), items.size());
    }

    private SourceFetcher findFetcher(SourceType type) {
        return sourceFetchers.stream()
            .filter(f -> f.getSourceType() == type)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No fetcher for type: " + type));
    }

    private NewsItem mapToNewsItem(Source source, FetchedItem item) {
        return NewsItem.builder()
            .source(source)
            .guid(item.getGuid())
            .title(item.getTitle())
            .link(item.getLink())
            .author(item.getAuthor())
            .publishedAt(item.getPublishedAt())
            .rawContent(item.getRawContent())
            .cleanContent(item.getCleanContent())
            .status(NewsItemStatus.PENDING)
            .build();
    }
}
```

### 4.4 FeedSchedulerJob

Replaces the old FeedIngestionJob. Runs every minute, finds sources due for refresh, enqueues them.

```java
// core/job/FeedSchedulerJob.java
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "application.jobs.feed-ingestion", name = "enabled", havingValue = "true")
public class FeedSchedulerJob {

    private final SourceRepository sourceRepository;
    private final FetchQueue fetchQueue;

    @Scheduled(fixedRateString = "${application.jobs.feed-scheduling.interval:60000}")
    public void scheduleFeeds() {
        if (!fetchQueue.canAccept()) {
            log.warn("Queue full, skipping scheduling cycle");
            return;
        }

        List<Source> dueForRefresh = sourceRepository.findSourcesDueForRefresh();

        log.debug("Found {} sources due for refresh", dueForRefresh.size());

        for (Source source : dueForRefresh) {
            // Mark as queued
            source.setFetchStatus(FetchStatus.QUEUED);
            source.setQueuedAt(LocalDateTime.now());
            sourceRepository.save(source);

            // Add to queue
            fetchQueue.enqueue(source.getId());
        }
    }
}
```

### 4.5 Repository Query

```java
// core/repository/SourceRepository.java

@Query("""
    SELECT s FROM Source s
    WHERE s.status = 'ACTIVE'
      AND s.fetchStatus = 'IDLE'
      AND (s.lastFetchedAt IS NULL
           OR s.lastFetchedAt < :cutoff)
    ORDER BY s.lastFetchedAt ASC NULLS FIRST
    """)
List<Source> findSourcesDueForRefresh(@Param("cutoff") LocalDateTime cutoff);

// Or with per-source interval (more complex):
@Query(value = """
    SELECT * FROM sources s
    WHERE s.status = 'ACTIVE'
      AND s.fetch_status = 'IDLE'
      AND (s.last_fetched_at IS NULL
           OR NOW() > s.last_fetched_at + (s.refresh_interval_minutes || ' minutes')::interval)
    ORDER BY s.last_fetched_at ASC NULLS FIRST
    LIMIT 100
    """, nativeQuery = true)
List<Source> findSourcesDueForRefresh();
```

## 5. Configuration

### 5.1 Application Properties

```yaml
application:
  jobs:
    feed-ingestion:
      enabled: true
      queue-capacity: 1000      # Max items in queue
      worker-count: 4           # Parallel fetch workers
    feed-scheduling:
      interval: 60000           # Check every 1 minute
```

### 5.2 Local Development

```yaml
# application-local.properties
application.jobs.feed-ingestion.enabled: false
application.jobs.feed-scheduling.interval: 300000  # 5 minutes for local
```

## 6. Failure Handling

### 6.1 Stuck Sources Recovery

Sources stuck in QUEUED or FETCHING status (e.g., after crash) need recovery:

```java
// core/job/StuckSourceRecoveryJob.java
@Component
@RequiredArgsConstructor
@Slf4j
public class StuckSourceRecoveryJob {

    private final SourceRepository sourceRepository;

    @Scheduled(fixedRate = 300000)  // Every 5 minutes
    public void recoverStuckSources() {
        LocalDateTime stuckThreshold = LocalDateTime.now().minusMinutes(10);

        // Reset sources stuck in QUEUED for > 10 minutes
        int queuedReset = sourceRepository.resetStuckQueuedSources(stuckThreshold);
        if (queuedReset > 0) {
            log.warn("Reset {} stuck QUEUED sources", queuedReset);
        }

        // Reset sources stuck in FETCHING for > 10 minutes
        int fetchingReset = sourceRepository.resetStuckFetchingSources(stuckThreshold);
        if (fetchingReset > 0) {
            log.warn("Reset {} stuck FETCHING sources", fetchingReset);
        }
    }
}
```

```java
// Repository methods
@Modifying
@Query("""
    UPDATE Source s
    SET s.fetchStatus = 'IDLE', s.queuedAt = NULL
    WHERE s.fetchStatus = 'QUEUED' AND s.queuedAt < :threshold
    """)
int resetStuckQueuedSources(@Param("threshold") LocalDateTime threshold);

@Modifying
@Query("""
    UPDATE Source s
    SET s.fetchStatus = 'IDLE', s.fetchStartedAt = NULL
    WHERE s.fetchStatus = 'FETCHING' AND s.fetchStartedAt < :threshold
    """)
int resetStuckFetchingSources(@Param("threshold") LocalDateTime threshold);
```

### 6.2 Dead Letter Queue (Future)

When migrating to SQS, failed items can be sent to a dead-letter queue for manual inspection:

```java
// Future: SQS implementation
public class SqsFetchQueue implements FetchQueue {

    @SqsListener(value = "feed-fetch-dlq", deletionPolicy = NEVER)
    public void handleDeadLetter(Message<String> message) {
        log.error("Dead letter: source_id={}", message.getPayload());
        // Alert, store for manual review, etc.
    }
}
```

## 7. Migration Path to AWS SQS

### 7.1 SQS Implementation

```java
// core/queue/SqsFetchQueue.java
@Component
@Profile("aws")
@RequiredArgsConstructor
@Slf4j
public class SqsFetchQueue implements FetchQueue {

    private final SqsTemplate sqsTemplate;

    @Value("${application.aws.sqs.feed-fetch-queue}")
    private String queueUrl;

    @Override
    public void enqueue(Long sourceId) {
        sqsTemplate.send(queueUrl, sourceId.toString());
    }

    @Override
    public boolean canAccept() {
        return true;  // SQS handles backpressure
    }

    @Override
    public int size() {
        // Use SQS GetQueueAttributes API
        return -1;  // Unknown for SQS
    }

    @SqsListener("${application.aws.sqs.feed-fetch-queue}")
    public void onMessage(String sourceIdStr, FetchWorker fetchWorker) {
        Long sourceId = Long.parseLong(sourceIdStr);
        fetchWorker.process(sourceId);
    }
}
```

### 7.2 Profile-Based Switching

```yaml
# application-aws.yml
spring:
  cloud:
    aws:
      sqs:
        enabled: true

application:
  aws:
    sqs:
      feed-fetch-queue: https://sqs.region.amazonaws.com/account/feed-fetch
```

```java
// Disable in-memory queue when using AWS
@Component
@Profile("!aws")
public class InMemoryFetchQueue implements FetchQueue { ... }
```

## 8. Monitoring

### 8.1 Metrics

```java
// core/metrics/FetchQueueMetrics.java
@Component
@RequiredArgsConstructor
public class FetchQueueMetrics {

    private final MeterRegistry registry;
    private final FetchQueue fetchQueue;

    @Scheduled(fixedRate = 10000)
    public void recordQueueMetrics() {
        registry.gauge("feed.queue.size", fetchQueue.size());
    }

    public void recordEnqueue(Long sourceId) {
        registry.counter("feed.queue.enqueue").increment();
    }

    public void recordProcessed(Long sourceId, boolean success, long durationMs) {
        registry.counter("feed.queue.processed",
            "success", String.valueOf(success)).increment();
        registry.timer("feed.queue.process.duration")
            .record(durationMs, TimeUnit.MILLISECONDS);
    }
}
```

### 8.2 Health Indicator

```java
// core/health/FetchQueueHealthIndicator.java
@Component
@RequiredArgsConstructor
public class FetchQueueHealthIndicator implements HealthIndicator {

    private final FetchQueue fetchQueue;
    private final SourceRepository sourceRepository;

    @Override
    public Health health() {
        int queueSize = fetchQueue.size();
        long stuckCount = sourceRepository.countStuckSources();

        Health.Builder builder = (queueSize > 900 || stuckCount > 10)
            ? Health.down()
            : Health.up();

        return builder
            .withDetail("queue_size", queueSize)
            .withDetail("stuck_sources", stuckCount)
            .build();
    }
}
```

## 9. Sequence Diagrams

### 9.1 Normal Flow

```
User              SourceService      SourceRepo       Scheduler        Queue          Worker
  │                    │                 │                │              │               │
  │ POST /sources      │                 │                │              │               │
  │───────────────────▶│                 │                │              │               │
  │                    │ save(source)    │                │              │               │
  │                    │────────────────▶│                │              │               │
  │                    │   [ACTIVE,IDLE] │                │              │               │
  │   201 Created      │◀────────────────│                │              │               │
  │◀───────────────────│                 │                │              │               │
  │                    │                 │                │              │               │
  │                    │                 │  (next minute) │              │               │
  │                    │                 │◀───────────────│              │               │
  │                    │                 │ findDueForRefresh             │               │
  │                    │                 │───────────────▶│              │               │
  │                    │                 │ [source]       │              │               │
  │                    │                 │◀───────────────│              │               │
  │                    │                 │ update QUEUED  │              │               │
  │                    │                 │◀───────────────│              │               │
  │                    │                 │                │ enqueue(id)  │               │
  │                    │                 │                │─────────────▶│               │
  │                    │                 │                │              │ process(id)   │
  │                    │                 │                │              │──────────────▶│
  │                    │                 │                │              │               │ fetch RSS
  │                    │                 │                │              │               │ save items
  │                    │                 │                │              │               │ update IDLE
  │                    │                 │◀──────────────────────────────────────────────│
```

### 9.2 New Source Immediate Pickup

Since new sources have `lastFetchedAt = NULL` and `fetchStatus = IDLE`, they are immediately eligible for scheduling:

```sql
-- This query will return newly created sources
SELECT * FROM sources
WHERE status = 'ACTIVE'
  AND fetch_status = 'IDLE'
  AND (last_fetched_at IS NULL OR ...)  -- NULL satisfies first condition
ORDER BY last_fetched_at ASC NULLS FIRST  -- NULLs come first
```

Maximum wait time for new source: **1 minute** (scheduler interval).

## 10. Testing Strategy

### 10.1 Unit Tests

| Component | Test Focus |
|-----------|------------|
| FetchWorker | Fetch success/failure, status transitions |
| FeedSchedulerJob | Query correctness, queue interaction |
| InMemoryFetchQueue | Capacity limits, worker lifecycle |

### 10.2 Integration Tests

| Test | Scope |
|------|-------|
| NewSourcePickupIT | Verify new source fetched within 2 minutes |
| QueueBackpressureIT | Verify behavior when queue is full |
| StuckRecoveryIT | Verify stuck sources are recovered |

### 10.3 Example Test

```java
@SpringBootTest
class NewSourcePickupIT {

    @Autowired SourceService sourceService;
    @Autowired NewsItemRepository newsItemRepository;

    @Test
    void newSourceShouldBeFetchedWithinTwoMinutes() {
        // Given: a mock RSS feed
        stubRssFeed("https://example.com/feed.xml");

        // When: create a new source
        Source source = sourceService.createSource(userId,
            new SourceCreateRequest("https://example.com/feed.xml", "Test"));

        // Then: items should appear within 2 minutes
        await().atMost(2, MINUTES).untilAsserted(() -> {
            List<NewsItem> items = newsItemRepository.findBySourceId(source.getId());
            assertThat(items).isNotEmpty();
        });
    }
}
```
