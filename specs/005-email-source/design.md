# Email Source - Design

## Overview

Add EMAIL as a new source type. Users create an email source with a generated UUID-based email address (`{uuid}@inbox.daybrief.ai`). Incoming emails trigger `EmailReceivedEvent`, which is processed by a dedicated listener that extracts news items using AI and stores them for the standard processing pipeline.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              Frontend                                    │
│  AddSourceDialog ─────► type selector (RSS | EMAIL)                     │
│  SourceDetailPage ────► shows email address + copy button for EMAIL     │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                              Backend                                     │
│                                                                          │
│  ┌──────────────────┐    ┌────────────────────┐                         │
│  │  SourceService   │───►│ EmailSourceFetcher │  (no-op fetcher)        │
│  │  (create source) │    └────────────────────┘                         │
│  └──────────────────┘                                                   │
│                                                                          │
│  ┌──────────────────────────────────────────────────────────────┐       │
│  │                  Email Ingestion Flow                         │       │
│  │                                                               │       │
│  │  EmailReceivedEvent ──► EmailIngestionListener                │       │
│  │                              │                                │       │
│  │                              ▼                                │       │
│  │                    ┌─────────────────┐                        │       │
│  │                    │ Store raw email │ (StorageProvider)      │       │
│  │                    └────────┬────────┘                        │       │
│  │                              │                                │       │
│  │                              ▼                                │       │
│  │                    ┌─────────────────┐                        │       │
│  │                    │ AiService       │                        │       │
│  │                    │ extractFromEmail│                        │       │
│  │                    └────────┬────────┘                        │       │
│  │                              │                                │       │
│  │                              ▼                                │       │
│  │                    ┌─────────────────┐                        │       │
│  │                    │ Create NewsItems│ (status: PENDING)      │       │
│  │                    └─────────────────┘                        │       │
│  └──────────────────────────────────────────────────────────────┘       │
│                                                                          │
│  ┌──────────────────────────────────────────────────────────────┐       │
│  │  ProcessingWorker (existing) ──► enrichWithScore for items   │       │
│  └──────────────────────────────────────────────────────────────┘       │
└─────────────────────────────────────────────────────────────────────────┘
```

## Components and Interfaces

### Backend

#### 1. SourceType Enum
**File:** `core/model/SourceType.java`

```java
public enum SourceType {
    RSS,
    EMAIL
}
```

#### 2. Source Entity Update
**File:** `core/model/Source.java`

Add field:
```java
@Column(name = "email_address", unique = true)
private UUID emailAddress;  // Only for EMAIL type
```

#### 3. EmailSourceFetcher
**File:** `provider/sourcefetch/EmailSourceFetcher.java`

Implements `SourceFetcher` for EMAIL type. No-op implementation since emails are pushed, not pulled.

```java
@Component
public class EmailSourceFetcher implements SourceFetcher {

    @Override
    public SourceType getSourceType() {
        return SourceType.EMAIL;
    }

    @Override
    public SourceValidationResult validate(String url) {
        // EMAIL sources always valid (no URL to validate)
        return SourceValidationResult.success("Email Source", "Receives emails at generated address");
    }

    @Override
    public List<FetchedItem> fetch(Source source, LocalDateTime lastFetchedAt) {
        // No periodic fetch - emails arrive via events
        return List.of();
    }
}
```

#### 4. SourceService Update
**File:** `core/service/SourceService.java`

Modify `createSource()` to handle EMAIL type:
- Skip URL validation for EMAIL
- Generate UUID for `emailAddress`
- Set URL to `email://{uuid}` as placeholder

```java
public SourceDTO createSource(...) {
    if (type == SourceType.EMAIL) {
        UUID emailUuid = UUID.randomUUID();
        String url = "email://" + emailUuid;
        // Skip URL validation, skip fetcher validation
        Source source = Source.builder()
            .dayBrief(dayBrief)
            .url(url)
            .name(name)  // required for EMAIL
            .type(SourceType.EMAIL)
            .emailAddress(emailUuid)
            .status(SourceStatus.ACTIVE)
            .tags(tags)
            .refreshIntervalMinutes(0)  // no refresh for email
            .build();
        // ...
    }
    // existing RSS logic
}
```

#### 5. SourceDTO Update
**File:** `core/dto/SourceDTO.java`

Add field:
```java
private String emailAddress;  // Full address: {uuid}@inbound.morningdeck.com
```

Update `mapToDTO()` in SourceService to format the email address.

#### 6. EmailIngestionListener
**File:** `core/service/EmailIngestionListener.java`

New component that listens for `EmailReceivedEvent`:

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailIngestionListener {

    private final SourceRepository sourceRepository;
    private final NewsItemRepository newsItemRepository;
    private final AiService aiService;
    private final StorageProvider storageProvider;
    private final HtmlToMarkdownConverter htmlConverter;

    @Value("${application.email.domain:inbound.morningdeck.com}")
    private String emailDomain;

    @EventListener
    @Async
    public void handleEmailReceived(EmailReceivedEvent event) {
        EmailMessage email = event.getEmail();

        // Extract UUID from recipient address
        UUID emailUuid = extractEmailUuid(email.getTo());
        if (emailUuid == null) {
            log.warn("No valid recipient found in email {}", email.getMessageId());
            return;
        }

        // Find source by email address
        Source source = sourceRepository.findByEmailAddress(emailUuid).orElse(null);
        if (source == null || source.getStatus() != SourceStatus.ACTIVE) {
            log.warn("No active source for email address {}", emailUuid);
            return;
        }

        // Store raw email
        storeRawEmail(source.getId(), email);

        // Extract news items using AI
        processEmail(source, email);
    }

    private void processEmail(Source source, EmailMessage email) {
        String content = htmlConverter.convert(email.getContent());

        try {
            List<ExtractedNewsItem> items = aiService.extractFromEmail(
                email.getSubject(),
                content
            );

            for (int i = 0; i < items.size(); i++) {
                createNewsItem(source, email, items.get(i), i);
            }

            source.setLastFetchedAt(LocalDateTime.now());
            source.setLastError(null);
            sourceRepository.save(source);

        } catch (Exception e) {
            log.error("Failed to extract items from email {}: {}", email.getMessageId(), e.getMessage());
            // Create fallback item
            createFallbackNewsItem(source, email, e.getMessage());
        }
    }

    private void createNewsItem(Source source, EmailMessage email, ExtractedNewsItem item, int index) {
        String guid = email.getMessageId() + "#" + index;

        // Skip if already exists
        if (newsItemRepository.existsBySourceIdAndGuid(source.getId(), guid)) {
            return;
        }

        String link = item.url() != null ? item.url() : "mailto:" + email.getMessageId();

        NewsItem newsItem = NewsItem.builder()
            .source(source)
            .guid(guid)
            .title(item.title())
            .link(link)
            .author(email.getFrom())
            .publishedAt(email.getReceivedDate())
            .rawContent(email.getContent())
            .cleanContent(item.summary())  // Use extraction summary as clean content
            .summary(item.summary())
            .status(NewsItemStatus.PENDING)
            .build();

        newsItemRepository.save(newsItem);
    }

    private void storeRawEmail(UUID sourceId, EmailMessage email) {
        String key = "emails/" + sourceId + "/" + email.getMessageId() + ".json";
        byte[] content = serializeEmail(email);
        storageProvider.store(UUID.nameUUIDFromBytes(key.getBytes()), content, "application/json");
    }
}
```

#### 7. AiService Interface Update
**File:** `provider/ai/AiService.java`

Add method:
```java
List<ExtractedNewsItem> extractFromEmail(String subject, String content);
```

#### 8. ExtractedNewsItem Record
**File:** `provider/ai/model/ExtractedNewsItem.java`

```java
public record ExtractedNewsItem(
    String title,
    String summary,
    String url  // nullable
) {}
```

#### 9. SpringAiService Update
**File:** `provider/ai/SpringAiService.java`

Add implementation:
```java
@Value("classpath:prompts/email-extract.st")
private Resource emailExtractPromptResource;

@Override
public List<ExtractedNewsItem> extractFromEmail(String subject, String content) {
    log.debug("Extracting news items from email: {}", subject);
    try {
        return chatClient.prompt()
            .user(u -> u.text(loadPrompt(emailExtractPromptResource))
                .param("subject", subject)
                .param("content", truncate(content, 8000)))
            .call()
            .entity(new ParameterizedTypeReference<List<ExtractedNewsItem>>() {});
    } catch (Exception e) {
        log.error("Failed to extract from email: {}", e.getMessage(), e);
        throw new AiProcessingException("Failed to extract news items: " + e.getMessage());
    }
}
```

#### 10. Email Extract Prompt
**File:** `resources/prompts/email-extract.st`

```
Extract news items from this newsletter email. For each distinct news story or article mentioned:

1. title: A concise title for the news item
2. summary: 2-3 sentence summary of the key points
3. url: The original article URL if present in the email (null if not found)

Return up to 5 items, prioritizing the most prominent/relevant stories.

Email Subject: {subject}
Email Content: {content}
```

#### 11. SourceRepository Update
**File:** `core/repository/SourceRepository.java`

Add method:
```java
Optional<Source> findByEmailAddress(UUID emailAddress);
```

#### 12. Flyway Migration
**File:** `resources/db/migrations/V11__email_source.sql`

```sql
ALTER TABLE sources ADD COLUMN email_address UUID UNIQUE;
CREATE INDEX idx_sources_email_address ON sources(email_address) WHERE email_address IS NOT NULL;
```

### Frontend

#### 1. Types Update
**File:** `src/types/index.ts`

```typescript
export type SourceType = 'RSS' | 'EMAIL';

export interface SourceDTO {
  // ... existing fields
  emailAddress: string | null;  // Full email: {uuid}@inbound.morningdeck.com
}

export interface CreateSourceRequest {
  briefingId: string;
  url?: string;        // Optional for EMAIL
  name: string;        // Required for EMAIL
  type: SourceType;
  tags?: string[];
}
```

#### 2. AddSourceDialog Update
**File:** `src/components/sources/AddSourceDialog.tsx`

Add source type selector. Show URL field only for RSS, name required for EMAIL.

```tsx
const [sourceType, setSourceType] = useState<SourceType>('RSS');

// Validation schema based on type
const getSchema = (type: SourceType) => {
  if (type === 'EMAIL') {
    return z.object({
      name: z.string().min(1, 'Name is required'),
      tags: z.array(z.string()).optional(),
    });
  }
  return z.object({
    url: z.string().url('Please enter a valid URL'),
    name: z.string().optional(),
    tags: z.array(z.string()).optional(),
  });
};

// In form:
<div className="space-y-2">
  <Label>Source Type</Label>
  <div className="flex gap-2">
    <Button
      type="button"
      variant={sourceType === 'RSS' ? 'default' : 'outline'}
      onClick={() => setSourceType('RSS')}
    >
      RSS Feed
    </Button>
    <Button
      type="button"
      variant={sourceType === 'EMAIL' ? 'default' : 'outline'}
      onClick={() => setSourceType('EMAIL')}
    >
      Email
    </Button>
  </div>
</div>

{sourceType === 'RSS' && (
  // URL input field
)}

{sourceType === 'EMAIL' && (
  <p className="text-sm text-muted-foreground">
    An email address will be generated for this source.
  </p>
)}

// Name field always shown, required for EMAIL
```

#### 3. SourceDetailPage Update
**File:** `src/pages/SourceDetailPage.tsx`

Show email address with copy button for EMAIL sources:

```tsx
{source.type === 'EMAIL' && source.emailAddress && (
  <div className="flex items-center gap-2 p-3 bg-muted rounded-md">
    <Mail className="h-4 w-4 text-muted-foreground" />
    <code className="flex-1 text-sm">{source.emailAddress}</code>
    <Button
      variant="ghost"
      size="sm"
      onClick={() => {
        navigator.clipboard.writeText(source.emailAddress!);
        toast({ title: 'Copied', description: 'Email address copied to clipboard' });
      }}
    >
      <Copy className="h-4 w-4" />
    </Button>
  </div>
)}
```

Hide refresh button for EMAIL sources (no periodic fetch).

## Data Model

### Source Entity Changes

| Field | Type | Description |
|-------|------|-------------|
| emailAddress | UUID | Nullable. The UUID portion of the email address for EMAIL sources |

### NewsItem (no changes)

Items from email sources use existing fields:
- `guid`: `{emailMessageId}#{index}`
- `link`: Original URL or `mailto:{messageId}`
- `author`: Email sender
- `publishedAt`: Email received date
- `rawContent`: Full email content
- `summary`: AI-extracted summary

## Error Handling

| Scenario | Handling |
|----------|----------|
| Unknown recipient email | Log warning, discard email |
| Inactive/deleted source | Log warning, discard email |
| AI extraction fails | Create single fallback NewsItem with email subject as title, mark ERROR |
| Duplicate email (same messageId) | Skip via guid uniqueness check |
| Storage failure | Log error, continue with processing |

## Testing Strategy

### Unit Tests
- `EmailSourceFetcher`: Verify no-op behavior
- `SourceService.createSource()`: Test EMAIL type creates correct URL and emailAddress
- `EmailIngestionListener`: Mock dependencies, test email parsing and item creation

### Integration Tests
- Create EMAIL source, verify emailAddress generated
- Publish `EmailReceivedEvent`, verify NewsItems created
- Test duplicate email handling
- Test AI extraction failure fallback

## Performance Considerations

- Email processing is async (`@Async` on listener)
- AI extraction limited to 8000 chars of content
- Max 5 items per email to limit LLM calls
- Raw email storage is non-blocking

## Security Considerations

- Email addresses use UUID (not guessable)
- Source ownership verified via emailAddress lookup
- HTML content sanitized via HtmlToMarkdownConverter before AI processing
- Raw email stored encrypted in S3 (existing StorageProvider)

## Monitoring and Observability

### Logging
- Email received: `log.info("Processing email {} for source {}")`
- Items extracted: `log.info("Extracted {} items from email {}")`
- Failures: `log.error()` with full context

### Metrics (future)
- `email.received.count` - emails processed
- `email.items.extracted` - news items created
- `email.extraction.failures` - AI failures
