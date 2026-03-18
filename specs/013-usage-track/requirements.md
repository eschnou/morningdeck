# Usage Tracking — Requirements

## 1. Introduction

Track all AI API calls with detailed token usage metrics, feature attribution, and user association. This is an internal auditing system for cost analysis, debugging, and understanding feature consumption patterns.

**Note**: This is separate from the credit/billing system. Credits will be linked to domain objects (e.g., news items) independently.

## 2. Alignment with Product Vision

From `product.md`:
- **Processing Pipeline**: Multiple AI-powered features (summarization, scoring, content extraction, report generation)
- **Success Metrics**: Requires usage data to understand feature adoption and cost per user
- **Operational Excellence**: Need visibility into AI API costs and performance

## 3. Functional Requirements

### FR-1: API Call Logging

**User Story**: As a system administrator, I want every AI API call logged with full metadata, so that I can audit usage and understand costs.

**Acceptance Criteria**:
- Every call to `AiService` methods creates a usage log entry
- Log entry includes: user ID, feature key, timestamp, success/failure status
- Log entry persists even if the AI call fails (with error details)
- Logging does not block or slow down the main request flow

### FR-2: Token Usage Tracking

**User Story**: As a billing system, I want to capture exact token counts for each API call, so that I can calculate precise costs.

**Acceptance Criteria**:
- Capture input tokens (prompt tokens sent to API)
- Capture output tokens (completion tokens received)
- Capture total tokens (sum, for convenience)
- Store model identifier used for the call (e.g., `gpt-4o`)
- Handle cases where token data is unavailable (set to null, not zero)

### FR-3: Feature Attribution

**User Story**: As a product analyst, I want each API call tagged with a feature key, so that I can understand which features consume the most resources.

**Acceptance Criteria**:
- Define feature keys as an enum for type safety
- Feature keys map to `AiService` operations:

| Feature Key | AiService Method | Description |
|-------------|------------------|-------------|
| `ENRICH` | `enrich()` | Article summarization, topic extraction, entity detection |
| `SCORE` | `score()` | Article relevance scoring against briefing criteria |
| `ENRICH_SCORE` | `enrichWithScore()` | Combined enrichment and scoring (single LLM call) |
| `EMAIL_EXTRACT` | `extractFromEmail()` | Newsletter parsing into news items |
| `WEB_EXTRACT` | `extractFromWeb()` | Web page content extraction |
| `REPORT_GEN` | `generateReportEmailContent()` | Daily report subject/summary generation |

- Feature key is required (not nullable) for every log entry

### FR-4: User Association

**User Story**: As an account manager, I want usage attributed to specific users, so that I can enforce per-user quotas and generate usage reports.

**Acceptance Criteria**:
- Every log entry links to a user ID (foreign key to `users` table)
- Support querying usage by user over time ranges
- User association derived from processing context (news item owner, briefing owner, etc.)

## 4. Non-Functional Requirements

### NFR-1: Performance

- Usage logging must be non-blocking (async or after-commit)
- Logging overhead < 5ms per API call
- Query endpoints return in < 200ms for typical requests
- Support logging at sustained rate of 100 calls/second

### NFR-2: Data Integrity

- Usage logs are immutable (append-only, no updates or deletes)
- Foreign key constraints to users table
- Timestamps stored in UTC with timezone awareness
- Handle orphaned logs gracefully if user is deleted (cascade or nullify policy)

### NFR-3: Reliability

- Logging failures must not fail the parent operation
- Failed logging attempts should be retried or logged to error sink
- No data loss during application restarts

### NFR-4: Security

- Usage data visible only to owning user and admins
- API endpoints require authentication
- Sensitive data (API keys, prompts) not stored in usage logs

### NFR-5: Observability

- Emit metrics for: total calls, tokens by feature, error rates
- Log warnings when token counts unavailable
- Dashboard-ready aggregation queries

## 5. Data Model (Conceptual)

```
ApiUsageLog {
  id: UUID (PK)
  user_id: UUID (FK -> users)
  feature_key: Enum (ENRICH, SCORE, ENRICH_SCORE, EMAIL_EXTRACT, WEB_EXTRACT, REPORT_GEN)
  model: String (e.g., "gpt-4o")
  input_tokens: Integer (nullable)
  output_tokens: Integer (nullable)
  total_tokens: Integer (nullable)
  success: Boolean
  error_message: String (nullable)
  duration_ms: Long
  created_at: Timestamp
}
```

## 6. Out of Scope

- Credit system integration (separate feature)
- Billing and payment provider integration
- Real-time usage alerts/notifications
- Cost prediction or budget warnings
- Detailed prompt/response logging (privacy concerns)
- Multi-model cost normalization
