# Feed Queue System - Tasks

## Phase 1: Data Model

- [ ] Add `FetchStatus` enum (IDLE, QUEUED, FETCHING)
- [ ] Add fields to Source entity:
  - `fetchStatus` (FetchStatus, default IDLE)
  - `refreshIntervalMinutes` (Integer, default 15)
  - `queuedAt` (LocalDateTime)
  - `fetchStartedAt` (LocalDateTime)
- [ ] Create Flyway migration V5__Feed_queue_support.sql
- [ ] Add index for scheduler query
- [ ] Update SourceDTO with new fields
- [ ] Update SourceCreateRequest/SourceUpdateRequest with `refreshIntervalMinutes`

## Phase 2: Queue Infrastructure

- [ ] Create `FetchQueue` interface
  - `enqueue(Long sourceId)`
  - `canAccept()`
  - `size()`
- [ ] Implement `InMemoryFetchQueue`
  - `BlockingQueue<Long>` with configurable capacity
  - Worker thread pool with configurable size
  - Graceful shutdown on application stop
- [ ] Add configuration properties:
  - `application.jobs.feed-ingestion.queue-capacity`
  - `application.jobs.feed-ingestion.worker-count`

## Phase 3: Worker Implementation

- [ ] Create `FetchWorker` component
  - Extract fetch logic from existing `FeedIngestionJob.processSource()`
  - Handle FETCHING status transitions
  - Handle success/error outcomes
  - Reset to IDLE after completion
- [ ] Add proper logging with source_id context
- [ ] Add metrics for fetch duration and success rate

## Phase 4: Scheduler Implementation

- [ ] Create `FeedSchedulerJob` (replaces scheduling logic in `FeedIngestionJob`)
  - Run every 1 minute
  - Query for sources due for refresh
  - Mark as QUEUED and enqueue
- [ ] Add repository method `findSourcesDueForRefresh()`
  - Native query with interval arithmetic
  - Order by lastFetchedAt NULLS FIRST
  - Limit to prevent queue overflow
- [ ] Add backpressure handling (skip cycle if queue full)

## Phase 5: Recovery & Reliability

- [ ] Create `StuckSourceRecoveryJob`
  - Run every 5 minutes
  - Reset QUEUED sources stuck > 10 minutes
  - Reset FETCHING sources stuck > 10 minutes
  - Log warnings for recovered sources
- [ ] Add repository methods:
  - `resetStuckQueuedSources(threshold)`
  - `resetStuckFetchingSources(threshold)`
  - `countStuckSources()`

## Phase 6: Observability

- [ ] Add `FetchQueueMetrics`
  - Gauge: queue size
  - Counter: enqueue operations
  - Counter: processed (success/failure)
  - Timer: processing duration
- [ ] Add `FetchQueueHealthIndicator`
  - DOWN if queue > 90% capacity
  - DOWN if > 10 stuck sources
- [ ] Update logging with structured fields

## Phase 7: Cleanup & Migration

- [ ] Remove old `FeedIngestionJob` (or rename to `FeedSchedulerJob`)
- [ ] Update configuration:
  - Change interval property names
  - Add new properties for queue/workers
- [ ] Update local development config
- [ ] Update existing tests

## Phase 8: Testing

- [ ] Unit tests:
  - `FetchWorkerTest` - status transitions, error handling
  - `InMemoryFetchQueueTest` - capacity, threading
  - `FeedSchedulerJobTest` - query logic, enqueue calls
  - `StuckSourceRecoveryJobTest` - recovery logic
- [ ] Integration tests:
  - `NewSourcePickupIT` - verify < 2 min pickup
  - `RefreshIntervalIT` - verify per-source intervals
  - `StuckRecoveryIT` - verify automatic recovery

## Phase 9: Documentation

- [ ] Update API documentation (Swagger annotations)
- [ ] Update CLAUDE.md if needed
- [ ] Add runbook for monitoring queue health
