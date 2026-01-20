# Feed Queue System - Requirements

## 1. Problem Statement

Currently, when a user creates a new RSS feed source, they must wait up to 15 minutes for the next scheduled poll cycle before any news items appear. This creates a poor user experience where the user adds a feed and sees an empty source.

Additionally, all sources are polled at the same interval regardless of how frequently they update, leading to inefficient resource usage.

## 2. Goals

### 2.1 Primary Goals

1. **Immediate pickup**: New sources should be fetched within 1-2 minutes of creation
2. **Per-source intervals**: Each source can have its own refresh interval (e.g., 5 min, 15 min, 1 hour)
3. **No duplicate processing**: Prevent the same source from being fetched by multiple workers simultaneously
4. **Future-proof**: Architecture should support easy migration to external queues (AWS SQS)

### 2.2 Non-Goals

- External queue implementation (SQS) in this phase
- Dead-letter queue functionality (deferred to SQS phase)
- Priority queuing (all sources treated equally)

## 3. Functional Requirements

### FR-1: Scheduler Job
- The scheduler runs every 1 minute
- Finds all sources where:
  - Status is ACTIVE
  - Fetch status is IDLE
  - Either never fetched, or refresh interval has elapsed since last fetch
- Marks eligible sources as QUEUED and enqueues them

### FR-2: Queue System
- In-memory queue with configurable capacity (default: 1000)
- Exposes interface for future SQS replacement
- Provides backpressure signal when full

### FR-3: Worker Pool
- Configurable number of workers (default: 4)
- Each worker polls from queue and processes sources
- Updates fetch status through lifecycle: IDLE → QUEUED → FETCHING → IDLE

### FR-4: Per-Source Refresh Interval
- New field `refreshIntervalMinutes` on Source entity
- Default value: 15 minutes
- Configurable per source via API

### FR-5: Stuck Source Recovery
- Recovery job runs every 5 minutes
- Resets sources stuck in QUEUED > 10 minutes to IDLE
- Resets sources stuck in FETCHING > 10 minutes to IDLE
- Logs warnings when stuck sources are found

## 4. Non-Functional Requirements

### NFR-1: Performance
- Queue operations (enqueue/dequeue) must be O(1)
- Scheduler query must use proper indexes
- Worker pool should handle 4 concurrent fetches by default

### NFR-2: Reliability
- System must recover from crashes without manual intervention
- No source should be "lost" in the queue

### NFR-3: Observability
- Queue size metric exposed
- Processing duration metric exposed
- Health check for queue state

### NFR-4: Backwards Compatibility
- Existing sources should work without modification
- Default values applied via migration

## 5. API Changes

### 5.1 Source Create/Update

Add optional `refreshIntervalMinutes` field:

```json
POST /api/sources
{
  "url": "https://example.com/feed.xml",
  "name": "Example Feed",
  "refreshIntervalMinutes": 30
}
```

### 5.2 Source Response

Include new fields in response:

```json
{
  "id": "...",
  "url": "...",
  "fetchStatus": "IDLE",
  "refreshIntervalMinutes": 15,
  "lastFetchedAt": "2024-01-15T10:30:00Z",
  "queuedAt": null
}
```

## 6. Success Criteria

1. New source has news items within 2 minutes of creation
2. Sources with different refresh intervals are polled accordingly
3. No duplicate fetches occur for the same source
4. System recovers automatically from stuck states
5. Queue interface allows drop-in SQS replacement
