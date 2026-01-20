# Email Source - Implementation Tasks

## Phase 1: Backend Data Model & Source Creation

**Goal:** Enable creation of EMAIL sources via API with generated email address.

**Verification:** Create EMAIL source via API, verify emailAddress returned in response.

### Tasks

#### 1.1 Add EMAIL to SourceType enum
**File:** `core/model/SourceType.java`

Add `EMAIL` value to enum.

**Test:** Compile check only.

#### 1.2 Add emailAddress field to Source entity
**File:** `core/model/Source.java`

```java
@Column(name = "email_address", unique = true)
private UUID emailAddress;
```

**Test:** Unit test entity creation with emailAddress.

#### 1.3 Create Flyway migration
**File:** `resources/db/migrations/V11__email_source.sql`

```sql
ALTER TABLE sources ADD COLUMN email_address UUID UNIQUE;
CREATE INDEX idx_sources_email_address ON sources(email_address) WHERE email_address IS NOT NULL;
```

**Test:** Migration runs without errors on test database.

#### 1.4 Add SourceRepository method
**File:** `core/repository/SourceRepository.java`

```java
Optional<Source> findByEmailAddress(UUID emailAddress);
```

**Test:** Unit test repository method.

#### 1.5 Add emailAddress to SourceDTO
**File:** `core/dto/SourceDTO.java`

```java
private String emailAddress;  // Full email: {uuid}@inbound.morningdeck.com
```

Remove `@NotBlank` from url field (conditional validation).

**Test:** DTO serialization test.

#### 1.6 Create EmailSourceFetcher
**File:** `provider/sourcefetch/EmailSourceFetcher.java`

No-op fetcher implementing `SourceFetcher`:
- `getSourceType()` returns `EMAIL`
- `validate()` returns success with static title/description
- `fetch()` returns empty list

**Test:** `EmailSourceFetcherTest`
- Verify getSourceType returns EMAIL
- Verify validate returns success
- Verify fetch returns empty list

#### 1.7 Update SourceService.createSource()
**File:** `core/service/SourceService.java`

Handle EMAIL type:
- Skip URL validation for EMAIL
- Generate UUID for emailAddress
- Set URL to `email://{uuid}`
- Name is required (throw if blank)
- Set refreshIntervalMinutes to 0

Update `mapToDTO()`:
- Format emailAddress as `{uuid}@inbound.morningdeck.com` using config property

Add config:
```java
@Value("${application.email.domain:inbound.morningdeck.com}")
private String emailDomain;
```

**Test:** `SourceServiceTest`
- Create EMAIL source returns valid emailAddress
- Create EMAIL source without name throws exception
- Create EMAIL source sets correct URL format

#### 1.8 Update SourceController validation
**File:** `core/controller/SourceController.java`

Update createSource to not require URL for EMAIL type.

**Test:** Integration test in Phase 1 verification.

### Phase 1 Integration Test
**File:** `core/controller/EmailSourceIT.java`

```java
@Test
void shouldCreateEmailSource() {
    // Create EMAIL source with name only
    // Verify response has emailAddress formatted correctly
    // Verify database has emailAddress UUID
}

@Test
void shouldRejectEmailSourceWithoutName() {
    // Create EMAIL source without name
    // Expect 400 Bad Request
}
```

---

## Phase 2: AI Email Extraction

**Goal:** Implement AI service method to extract news items from email content.

**Verification:** Unit tests pass for extraction with mock and real prompts.

### Tasks

#### 2.1 Create ExtractedNewsItem record
**File:** `provider/ai/model/ExtractedNewsItem.java`

```java
public record ExtractedNewsItem(
    String title,
    String summary,
    String url
) {}
```

**Test:** Record instantiation test.

#### 2.2 Add extractFromEmail to AiService interface
**File:** `provider/ai/AiService.java`

```java
List<ExtractedNewsItem> extractFromEmail(String subject, String content);
```

**Test:** Interface compile check.

#### 2.3 Create email-extract prompt
**File:** `resources/prompts/email-extract.st`

```
Extract news items from this newsletter email. For each distinct news story or article mentioned:

1. title: A concise title for the news item
2. summary: 2-3 sentence summary of the key points
3. url: The original article URL if present in the email (null if not found)

Return up to 5 items, prioritizing the most prominent/relevant stories. If the email contains no distinct news items, return a single item summarizing the email content.

Email Subject: {subject}
Email Content: {content}
```

**Test:** File exists check.

#### 2.4 Implement extractFromEmail in MockAiService
**File:** `provider/ai/MockAiService.java`

Return deterministic mock items:
```java
@Override
public List<ExtractedNewsItem> extractFromEmail(String subject, String content) {
    return List.of(
        new ExtractedNewsItem(
            "Mock: " + subject,
            "This is a mock extraction from the email about " + subject,
            null
        )
    );
}
```

**Test:** `MockAiServiceTest.extractFromEmail()`

#### 2.5 Implement extractFromEmail in SpringAiService
**File:** `provider/ai/SpringAiService.java`

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

**Test:** Integration test with OpenAI (manual/optional).

---

## Phase 3: Email Ingestion Listener

**Goal:** Process incoming emails and create NewsItems.

**Verification:** Publish EmailReceivedEvent, verify NewsItems created in database.

### Tasks

#### 3.1 Add existsBySourceIdAndGuid to NewsItemRepository
**File:** `core/repository/NewsItemRepository.java`

```java
boolean existsBySourceIdAndGuid(UUID sourceId, String guid);
```

**Test:** Repository method test.

#### 3.2 Create EmailIngestionListener
**File:** `core/service/EmailIngestionListener.java`

Event listener that:
1. Extracts UUID from recipient email address
2. Looks up Source by emailAddress
3. Validates source exists and is ACTIVE
4. Stores raw email via StorageProvider
5. Calls AiService.extractFromEmail()
6. Creates NewsItem for each extracted item
7. Updates source.lastFetchedAt
8. On failure: creates fallback NewsItem with ERROR status

Key methods:
- `handleEmailReceived(EmailReceivedEvent event)` - main entry point
- `extractEmailUuid(List<String> recipients)` - parse UUID from email address
- `storeRawEmail(UUID sourceId, EmailMessage email)` - store to S3
- `processEmail(Source source, EmailMessage email)` - orchestrate extraction
- `createNewsItem(Source, EmailMessage, ExtractedNewsItem, int index)` - create item
- `createFallbackNewsItem(Source, EmailMessage, String errorMessage)` - error case

**Test:** `EmailIngestionListenerTest`
- Valid email creates NewsItems
- Unknown recipient logs warning and returns
- Inactive source logs warning and returns
- AI failure creates fallback item with ERROR status
- Duplicate email (same messageId) is idempotent

#### 3.3 Add application.email.domain config
**File:** `application.yml` and `application-local.yml`

```yaml
application:
  email:
    domain: inbound.morningdeck.com
```

**Test:** Config loads correctly.

### Phase 3 Integration Test
**File:** `core/service/EmailIngestionIT.java`

```java
@Test
void shouldProcessEmailAndCreateNewsItems() {
    // Create EMAIL source
    // Publish EmailReceivedEvent
    // Verify NewsItems created
    // Verify source.lastFetchedAt updated
}

@Test
void shouldHandleDuplicateEmail() {
    // Create EMAIL source
    // Publish same EmailReceivedEvent twice
    // Verify only one set of NewsItems created
}

@Test
void shouldCreateFallbackItemOnAiFailure() {
    // Create EMAIL source
    // Configure AI to throw
    // Publish EmailReceivedEvent
    // Verify single ERROR NewsItem created
}
```

---

## Phase 4: Frontend - Add Source Dialog

**Goal:** Update AddSourceDialog to support EMAIL type selection.

**Verification:** User can create EMAIL source from UI, sees generated email address.

### Tasks

#### 4.1 Update SourceType in types
**File:** `src/types/index.ts`

```typescript
export type SourceType = 'RSS' | 'EMAIL';

export interface SourceDTO {
  // ... existing
  emailAddress: string | null;
}

export interface CreateSourceRequest {
  briefingId: string;
  url?: string;  // Optional for EMAIL
  name: string;
  type: SourceType;
  tags?: string[];
}
```

**Test:** TypeScript compile check.

#### 4.2 Update AddSourceDialog
**File:** `src/components/sources/AddSourceDialog.tsx`

Changes:
- Add sourceType state (default: 'RSS')
- Add type selector buttons (RSS Feed | Email)
- Conditionally show URL field (RSS only)
- Make name required for EMAIL
- Show helper text for EMAIL explaining address generation
- Submit with type in request

**Test:** Manual UI verification.

#### 4.3 Update API client if needed
**File:** `src/lib/api.ts`

Ensure createBriefingSource passes type parameter.

**Test:** API call includes type.

### Phase 4 Manual Verification
1. Open AddSourceDialog
2. Select "Email" type
3. Enter name "My Newsletter"
4. Submit
5. Verify source created with emailAddress displayed

---

## Phase 5: Frontend - Source Detail Page

**Goal:** Display email address with copy button for EMAIL sources.

**Verification:** EMAIL source detail page shows address and copy works.

### Tasks

#### 5.1 Update SourceDetailPage
**File:** `src/pages/SourceDetailPage.tsx`

Changes:
- Import Mail and Copy icons from lucide-react
- Add email address display section for EMAIL sources
- Add copy-to-clipboard button with toast notification
- Hide Refresh button for EMAIL sources
- Hide URL link for EMAIL sources (or show mailto: link)

```tsx
{source.type === 'EMAIL' && source.emailAddress && (
  <div className="flex items-center gap-2 p-3 bg-muted rounded-md">
    <Mail className="h-4 w-4 text-muted-foreground" />
    <code className="flex-1 text-sm font-mono">{source.emailAddress}</code>
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

**Test:** Manual UI verification.

#### 5.2 Update SourceCard (if used in lists)
**File:** `src/components/sources/SourceCard.tsx`

Show email icon instead of RSS icon for EMAIL sources.

**Test:** Manual UI verification.

### Phase 5 Manual Verification
1. Create EMAIL source
2. Navigate to source detail page
3. Verify email address displayed
4. Click copy button
5. Verify toast shows "Copied"
6. Paste elsewhere to confirm clipboard content
7. Verify Refresh button is hidden

---

## Phase 6: End-to-End Testing

**Goal:** Verify complete flow from source creation to news item display.

**Verification:** Full E2E test passes.

### Tasks

#### 6.1 Create E2E test scenario
**File:** `backend/src/test/java/be/transcode/daybrief/server/e2e/EmailSourceE2EIT.java`

```java
@Test
void fullEmailSourceFlow() {
    // 1. Create user and briefing
    // 2. Create EMAIL source via API
    // 3. Verify emailAddress in response
    // 4. Simulate email receipt (publish event)
    // 5. Verify NewsItems created via API
    // 6. Verify items appear in source's news list
}
```

#### 6.2 Update existing tests
Review and update any existing tests that assume SourceType.RSS only.

**Files to check:**
- `SourceIT.java` - add EMAIL source tests
- Any tests creating Source entities

### Phase 6 Verification
- All unit tests pass: `mvn test`
- All integration tests pass: `mvn verify`
- Manual E2E flow works in local environment
