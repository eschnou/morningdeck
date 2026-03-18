# Web Source Feature Design

## Overview

Add `WEB` as a third source type alongside `RSS` and `EMAIL`. Web sources fetch HTML from a URL and use LLM extraction to identify news items based on a user-defined prompt.

**Flow:**
```
URL → Fetch HTML → Convert to Markdown → LLM Extraction → NewsItems
```

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Frontend                                 │
│  AddSourceDialog → apiClient → SourceCard/SourceDetailPage      │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    SourceController                              │
│                POST /sources (type=WEB)                          │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     SourceService                                │
│         createSource() → WebFetcher.validate()                   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      FetchWorker                                 │
│    WebFetcher.fetch() → AiService.extractFromWeb() → NewsItems  │
└─────────────────────────────────────────────────────────────────┘
```

## Components and Interfaces

### Backend

#### 1. SourceType Enum
**File:** `backend/src/main/java/be/transcode/daybrief/server/core/model/SourceType.java`

```java
public enum SourceType {
    RSS,
    EMAIL,
    WEB
}
```

#### 2. Source Entity
**File:** `backend/src/main/java/be/transcode/daybrief/server/core/model/Source.java`

Add field:
```java
@Column(name = "extraction_prompt", length = 2048)
private String extractionPrompt;
```

#### 3. WebFetcher
**File:** `backend/src/main/java/be/transcode/daybrief/server/provider/sourcefetch/WebFetcher.java`

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class WebFetcher implements SourceFetcher {

    private final HtmlToMarkdownConverter htmlToMarkdownConverter;
    private final AiService aiService;
    private final HttpClient httpClient;

    @Override
    public SourceType getSourceType() {
        return SourceType.WEB;
    }

    @Override
    public SourceValidationResult validate(String url) {
        // Fetch URL, return success if HTTP 200
        // Extract page title for feedTitle
    }

    @Override
    public List<FetchedItem> fetch(Source source, LocalDateTime lastFetchedAt) {
        // 1. Fetch HTML from source.url
        // 2. Convert to markdown
        // 3. Call aiService.extractFromWeb(markdown, source.extractionPrompt)
        // 4. Map ExtractedWebItem to FetchedItem with normalized link as GUID
    }
}
```

#### 4. AiService Extension
**File:** `backend/src/main/java/be/transcode/daybrief/server/provider/ai/AiService.java`

Add method:
```java
List<ExtractedWebItem> extractFromWeb(String pageContent, String extractionPrompt);
```

**Response model:**
```java
public record ExtractedWebItem(
    String title,
    String content,
    String link
) {}
```

#### 5. SourceService Update
**File:** `backend/src/main/java/be/transcode/daybrief/server/core/service/SourceService.java`

Update `createSource()` to handle WEB type:
- Require URL and extractionPrompt
- Validate URL via WebFetcher.validate()
- Store extractionPrompt on entity
- Default refreshIntervalMinutes: 60

Update `updateSource()` to allow editing extractionPrompt for WEB sources.

#### 6. SourceDTO Update
**File:** `backend/src/main/java/be/transcode/daybrief/server/core/dto/SourceDTO.java`

Add field:
```java
private String extractionPrompt;
```

#### 7. URL Normalizer Utility
**File:** `backend/src/main/java/be/transcode/daybrief/server/provider/sourcefetch/UrlNormalizer.java`

```java
public class UrlNormalizer {
    public static String normalize(String url) {
        // Lowercase hostname
        // Remove trailing slash
        // Remove tracking params: utm_*, ref, fbclid, etc.
        // Return normalized URL as GUID
    }

    public static String resolveRelative(String baseUrl, String relativeUrl) {
        // Convert relative URLs to absolute
    }
}
```

### Frontend

#### 1. Type Definition
**File:** `frontend/src/types/index.ts`

```typescript
export type SourceType = 'RSS' | 'EMAIL' | 'WEB';

export interface SourceDTO {
  // ... existing fields
  extractionPrompt: string | null;  // Add this
}

export interface CreateSourceRequest {
  // ... existing fields
  extractionPrompt?: string;  // Add this
}

export interface UpdateSourceRequest {
  // ... existing fields
  extractionPrompt?: string;  // Add this
}
```

#### 2. AddSourceDialog Update
**File:** `frontend/src/components/sources/AddSourceDialog.tsx`

Changes:
- Add WEB toggle button with Globe icon
- Add validation schema for WEB (requires url and extractionPrompt)
- Add extractionPrompt textarea field (shown when type=WEB)
- Add placeholder example prompts
- Include extractionPrompt in submission payload

```tsx
// Type toggle buttons
<Button variant={sourceType === 'WEB' ? 'default' : 'outline'} onClick={() => setSourceType('WEB')}>
  <Globe className="mr-2 h-4 w-4" />
  Web Page
</Button>

// Extraction prompt field (when type=WEB)
{sourceType === 'WEB' && (
  <div className="space-y-2">
    <Label htmlFor="extractionPrompt">Extraction Prompt</Label>
    <Textarea
      id="extractionPrompt"
      placeholder="Extract the top stories about AI..."
      value={formData.extractionPrompt}
      onChange={(e) => setFormData({ ...formData, extractionPrompt: e.target.value })}
    />
  </div>
)}
```

#### 3. SourceCard Update
**File:** `frontend/src/components/sources/SourceCard.tsx`

Add WEB icon condition:
```tsx
{source.type === 'WEB' ? (
  <Globe className="h-4 w-4 text-muted-foreground" />
) : source.type === 'EMAIL' ? (
  <Mail className="h-4 w-4 text-muted-foreground" />
) : (
  <Rss className="h-4 w-4 text-muted-foreground" />
)}
```

#### 4. SourceDetailPage Update
**File:** `frontend/src/pages/SourceDetailPage.tsx`

- Display URL for WEB sources (same as RSS)
- Show extractionPrompt in a collapsible section
- Enable refresh button for WEB sources

#### 5. SourceForm Update
**File:** `frontend/src/components/sources/SourceForm.tsx`

Add extractionPrompt field for WEB sources (editable).

## Data Models

### Database Migration
**File:** `backend/src/main/resources/db/migration/V{next}__add_web_source.sql`

```sql
ALTER TABLE sources ADD COLUMN extraction_prompt VARCHAR(2048);
```

### Entity Changes

| Entity | Field | Type | Description |
|--------|-------|------|-------------|
| Source | extractionPrompt | String(2048) | LLM prompt for WEB sources |

### DTO Changes

| DTO | Field | Type | Description |
|-----|-------|------|-------------|
| SourceDTO | extractionPrompt | String | Included in responses |
| CreateSourceRequest | extractionPrompt | String | Required for WEB |
| UpdateSourceRequest | extractionPrompt | String | Editable for WEB |

## Error Handling

| Error | HTTP Code | Handling |
|-------|-----------|----------|
| URL unreachable | 400 | Return validation error with message |
| URL timeout (30s) | 400 | "URL took too long to respond" |
| Private IP blocked | 400 | "URLs to private networks are not allowed" |
| Non-HTTP(S) scheme | 400 | "Only HTTP and HTTPS URLs are supported" |
| LLM extraction fails | - | Create fallback NewsItem with ERROR status |
| No items extracted | - | Log warning, no items created |

## Testing Strategy

### Unit Tests
- `WebFetcherTest`: validate(), fetch(), URL normalization
- `UrlNormalizerTest`: normalize(), resolveRelative()
- `AiServiceTest`: extractFromWeb() with mock responses

### Integration Tests
- `SourceIT`: CRUD for WEB sources
- `FetchWorkerIT`: End-to-end fetch with WEB source

## Performance Considerations

| Concern | Mitigation |
|---------|------------|
| Large HTML pages | Truncate to 100KB before LLM |
| LLM latency | 60s timeout, async processing |
| Rate limiting | Min 60 min refresh interval |

## Security Considerations

| Threat | Mitigation |
|--------|------------|
| SSRF | Block private IPs (localhost, 10.x, 192.168.x, 172.16-31.x) |
| Malicious URLs | Validate HTTP/HTTPS only |
| XSS from content | Sanitize in frontend (existing pattern) |

## Monitoring and Observability

Use existing patterns:
- Log fetch attempts with source_id, duration, item_count
- Log extraction prompt and result count for debugging
- Source status transitions visible in UI
- lastError field captures failure details
