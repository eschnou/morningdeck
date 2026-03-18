# Email Source - Requirements

## 1. Introduction

Email Source enables users to receive newsletter-style emails as a content source within daybrief.ai. Each email source is assigned a unique email address (based on a UUID). Incoming emails are processed by AI to extract up to 5 news items with title, summary, and URL. Raw emails are stored for audit and debugging purposes.

## 2. Alignment with Product Vision

This feature implements the **Newsletter Inbox** source type from product.md (§5.1):
- Dedicated email addresses per source: `{uuid}@inbox.daybrief.ai`
- Emails processed through the content pipeline (§5.2): parse, summarize, score, tag
- Enables newsletters and email-based content to feed into daily briefings

Supports the product goal of aggregating content from diverse sources beyond RSS feeds.

## 3. Functional Requirements

### 3.1 Email Source Management

**US-3.1.1: Create Email Source**
> As a user, I want to create an email source with a unique email address, so that I can receive newsletters and emails for processing.

Acceptance Criteria:
- User provides: name, tags (optional)
- System generates a UUID for the email address
- Email address format: `{uuid}@inbox.daybrief.ai`
- Source created with type: EMAIL
- Source stores the UUID in a new `emailAddress` field (the UUID portion only)
- User can copy the full email address to configure forwarding or subscriptions

**US-3.1.2: View Email Source Address**
> As a user, I want to see the email address for my email source, so that I can subscribe to newsletters.

Acceptance Criteria:
- Source detail view displays the full email address
- Provides a "copy to clipboard" action for the email address
- Shows instructions for forwarding or subscribing

**US-3.1.3: Email Source Validation**
> As the system, I want to skip URL validation for email sources, since they don't have a traditional URL.

Acceptance Criteria:
- EMAIL sources do not require a URL
- URL field stores `email://{uuid}` as a placeholder for uniqueness constraint
- Validation step returns success without HTTP fetch

### 3.2 Email Reception

**US-3.2.1: Receive Email via Event**
> As the system, I want to process incoming emails via EmailReceivedEvent, so that email content is ingested.

Acceptance Criteria:
- Listen for `EmailReceivedEvent` from existing `emailreceive` provider
- Extract recipient address to identify target source UUID
- Lookup source by UUID from email address
- Reject emails for unknown/deleted sources (log and discard)
- Store raw email before processing

**US-3.2.2: Store Raw Email**
> As the system, I want to store the raw email content, so that it can be audited or reprocessed.

Acceptance Criteria:
- Store raw email using `StorageProvider` interface
- Storage key format: `emails/{sourceId}/{emailMessageId}.eml`
- Store email content, subject, from, date, and message ID
- Link stored email reference to generated news items

### 3.3 Email Processing

**US-3.3.1: Extract News Items from Email**
> As the system, I want to use AI to extract news items from incoming emails, so that newsletter content becomes searchable news items.

Acceptance Criteria:
- Send email content (subject + body) to AI service
- AI extracts up to 5 news items from the email
- Each extracted item has: title, summary, URL (if present)
- If no URL is available, use `mailto:{messageId}` as link placeholder
- Create NewsItem entities for each extracted item
- Set NewsItem.guid to `{emailMessageId}#{index}` for deduplication
- Set NewsItem.status to PENDING for further processing pipeline

**US-3.3.2: AI Extraction Prompt**
> As the system, I want a dedicated AI prompt for email extraction.

Acceptance Criteria:
- New prompt template: `email-extract.st`
- Input: email subject, email body (HTML converted to markdown)
- Output structure: array of `{title, summary, url}` objects
- Prompt instructs AI to:
  - Identify distinct news items/stories in the email
  - Extract the original article URL if present
  - Generate a concise title and summary for each item
  - Return max 5 items, prioritizing by relevance/prominence

**US-3.3.3: Handle Extraction Failures**
> As the system, I want to gracefully handle AI extraction failures.

Acceptance Criteria:
- If AI extraction fails, create single NewsItem with email subject as title
- Store full email content as rawContent
- Mark item for manual review (status: ERROR with message "AI extraction failed")
- Log error for monitoring

### 3.4 News Item Enrichment

**US-3.4.1: Process Extracted Items**
> As the system, I want extracted news items to flow through the standard processing pipeline.

Acceptance Criteria:
- Extracted items enter ProcessingWorker like RSS items
- AI enrichment (summary, tags, score) applied normally
- If item already has summary from extraction, skip summary generation
- Score against briefing criteria as normal

## 4. Non-Functional Requirements

### 4.1 Architecture

**Backend Changes:**
- Add `EMAIL` to `SourceType` enum
- Add `emailAddress` field to `Source` entity (nullable, for EMAIL type only)
- Create `EmailSourceFetcher` implementing `SourceFetcher` (returns empty list, no periodic fetch)
- Create `EmailIngestionService` to handle `EmailReceivedEvent`
- Add `extractNewsFromEmail()` method to `AiService` interface
- Create `email-extract.st` prompt template
- Add Flyway migration for email source fields

**Frontend Changes:**
- Update `SourceType` to include `'EMAIL'`
- Update `AddSourceDialog` to support EMAIL type selection
- EMAIL type shows name and tags fields only (no URL field)
- Display email address on source detail page with copy button
- Add instructions component for email source setup

**Storage:**
- Raw emails stored via existing `StorageProvider`
- New storage path: `emails/{sourceId}/{messageId}.eml`

### 4.2 Performance

- Email processing should complete within 60 seconds of receipt
- AI extraction timeout: 30 seconds
- Batch processing if multiple emails arrive simultaneously

### 4.3 Security

- Email addresses use UUID to prevent enumeration
- Only process emails from sources belonging to authenticated users
- Sanitize email HTML content before AI processing
- Rate limit: max 100 emails per source per day
- Email content scanned for malicious attachments (future consideration)

### 4.4 Reliability

- Idempotent email processing (dedupe by messageId)
- Failed emails stored for retry or manual review
- Source error status updated if repeated failures
- Dead letter handling for unprocessable emails

### 4.5 Data Model Changes

```
Source (updated)
+ emailAddress: UUID (nullable, unique, for EMAIL type only)

RawEmail (new entity, optional - or use storage only)
- id: UUID
- sourceId: UUID (FK)
- messageId: String
- fromAddress: String
- subject: String
- receivedAt: DateTime
- storageKey: String (path in StorageProvider)
- processed: Boolean
- createdAt: DateTime
```

### 4.6 API Changes

```
POST /api/v1/sources
- Request body accepts type: "EMAIL"
- For EMAIL type: url is not required, name is required
- Response includes emailAddress field (full email: {uuid}@inbound.morningdeck.com)

GET /api/v1/sources/{id}
- Response includes emailAddress field for EMAIL sources

SourceDTO (updated)
+ emailAddress: String (nullable, formatted as full email address)
```

### 4.7 AI Service Interface

```java
// New method in AiService
List<ExtractedNewsItem> extractNewsFromEmail(String subject, String content);

// New model
record ExtractedNewsItem(
    String title,
    String summary,
    String url  // nullable
) {}
```
