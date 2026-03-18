# Web Source Implementation Tasks

## Phase 1: Backend Data Model

**Goal:** Add WEB source type and extractionPrompt field to the data model.

**Verification:** Run `mvn test` - all existing tests pass.

### Tasks

1. **Add WEB to SourceType enum**
   - File: `backend/src/main/java/be/transcode/daybrief/server/core/model/SourceType.java`
   - Add `WEB` after `EMAIL`

2. **Add extractionPrompt field to Source entity**
   - File: `backend/src/main/java/be/transcode/daybrief/server/core/model/Source.java`
   - Add field: `@Column(name = "extraction_prompt", length = 2048) private String extractionPrompt;`

3. **Add extractionPrompt to SourceDTO**
   - File: `backend/src/main/java/be/transcode/daybrief/server/core/dto/SourceDTO.java`
   - Add field: `private String extractionPrompt;`

4. **Create database migration**
   - File: `backend/src/main/resources/db/migrations/V13__add_web_source.sql`
   - Content: `ALTER TABLE sources ADD COLUMN extraction_prompt VARCHAR(2048);`

5. **Update SourceService mapToDTO**
   - File: `backend/src/main/java/be/transcode/daybrief/server/core/service/SourceService.java`
   - Include extractionPrompt in the DTO mapping

---

## Phase 2: URL Normalizer Utility

**Goal:** Create utility for URL normalization (GUID generation) and relative URL resolution.

**Verification:** Unit tests pass for `UrlNormalizerTest`.

### Tasks

1. **Create UrlNormalizer class**
   - File: `backend/src/main/java/be/transcode/daybrief/server/provider/sourcefetch/UrlNormalizer.java`
   - Methods:
     - `normalize(String url)`: lowercase hostname, remove trailing slash, remove tracking params (utm_*, ref, fbclid, gclid)
     - `resolveRelative(String baseUrl, String relativeUrl)`: convert relative to absolute URL
   - Use `java.net.URI` for parsing

2. **Create UrlNormalizerTest**
   - File: `backend/src/test/java/be/transcode/daybrief/server/provider/sourcefetch/UrlNormalizerTest.java`
   - Test cases:
     - Lowercase hostname
     - Remove trailing slash
     - Remove utm_* parameters
     - Preserve path and non-tracking query params
     - Resolve relative URLs (/, ./, ../, protocol-relative)

---

## Phase 3: AiService Extension

**Goal:** Add extractFromWeb method to AiService for LLM-based item extraction.

**Verification:** Unit tests pass for `ClaudeAiServiceTest` with mocked responses.

### Tasks

1. **Create ExtractedWebItem record**
   - File: `backend/src/main/java/be/transcode/daybrief/server/provider/ai/model/ExtractedWebItem.java`
   - Fields: `title`, `content`, `link` (all String, all required)

2. **Add extractFromWeb to AiService interface**
   - File: `backend/src/main/java/be/transcode/daybrief/server/provider/ai/AiService.java`
   - Method: `List<ExtractedWebItem> extractFromWeb(String pageContent, String extractionPrompt);`

3. **Implement extractFromWeb in ClaudeAiService**
   - File: `backend/src/main/java/be/transcode/daybrief/server/provider/ai/ClaudeAiService.java`
   - Build prompt with page content and user's extraction prompt
   - Request JSON array of items (max 50)
   - Parse response into ExtractedWebItem list
   - Handle empty/invalid responses gracefully

4. **Add test for extractFromWeb**
   - File: `backend/src/test/java/be/transcode/daybrief/server/provider/ai/ClaudeAiServiceTest.java`
   - Mock Claude API response
   - Verify correct parsing of extracted items

---

## Phase 4: WebFetcher Implementation

**Goal:** Implement WebFetcher following SourceFetcher interface.

**Verification:** Unit tests pass for `WebFetcherTest`.

### Tasks

1. **Create WebFetcher class**
   - File: `backend/src/main/java/be/transcode/daybrief/server/provider/sourcefetch/WebFetcher.java`
   - Annotations: `@Slf4j`, `@Component`, `@RequiredArgsConstructor`
   - Dependencies: `HtmlToMarkdownConverter`, `AiService`
   - HttpClient with 30s timeout

2. **Implement getSourceType()**
   - Return `SourceType.WEB`

3. **Implement validate(String url)**
   - Fetch URL with HTTP GET
   - Return success if HTTP 200, extract page title from `<title>` tag
   - Return failure with error message otherwise
   - Timeout: 30 seconds

4. **Implement fetch(Source source, LocalDateTime lastFetchedAt)**
   - Fetch HTML from source.url
   - Convert HTML to markdown using HtmlToMarkdownConverter
   - Truncate to 100KB if larger
   - Call aiService.extractFromWeb(markdown, source.extractionPrompt)
   - Map each ExtractedWebItem to FetchedItem:
     - guid: UrlNormalizer.normalize(item.link)
     - title: item.title
     - link: UrlNormalizer.resolveRelative(source.url, item.link)
     - cleanContent: item.content
     - publishedAt: LocalDateTime.now()
   - Return list of FetchedItem

5. **Create WebFetcherTest**
   - File: `backend/src/test/java/be/transcode/daybrief/server/provider/sourcefetch/WebFetcherTest.java`
   - Mock HTTP responses and AiService
   - Test validate() success and failure
   - Test fetch() with extracted items
   - Test relative URL resolution
   - Test content truncation

---

## Phase 5: SourceService Integration

**Goal:** Update SourceService to handle WEB source creation and updates.

**Verification:** Integration tests pass for `SourceIT` with WEB sources.

### Tasks

1. **Update createSource() for WEB type**
   - File: `backend/src/main/java/be/transcode/daybrief/server/core/service/SourceService.java`
   - Add condition for `type == SourceType.WEB`:
     - Require URL (like RSS)
     - Require extractionPrompt (validation error if missing)
     - Validate URL via WebFetcher.validate()
     - Set extractionPrompt on entity
     - Default refreshIntervalMinutes: 60

2. **Update updateSource() for WEB type**
   - Allow editing extractionPrompt for WEB sources
   - Add extractionPrompt parameter to method signature

3. **Update SourceController**
   - File: `backend/src/main/java/be/transcode/daybrief/server/core/controller/SourceController.java`
   - Pass extractionPrompt from request DTO to service

4. **Add WEB source integration tests**
   - File: `backend/src/test/java/be/transcode/daybrief/server/core/SourceIT.java`
   - Test create WEB source with URL and extractionPrompt
   - Test validation error when extractionPrompt missing
   - Test update extractionPrompt
   - Test delete WEB source

---

## Phase 6: Frontend Types and API

**Goal:** Update frontend types to support WEB sources.

**Verification:** TypeScript compiles without errors (`npm run build`).

### Tasks

1. **Update SourceType**
   - File: `frontend/src/types/index.ts`
   - Change to: `export type SourceType = 'RSS' | 'EMAIL' | 'WEB';`

2. **Add extractionPrompt to SourceDTO**
   - File: `frontend/src/types/index.ts`
   - Add field: `extractionPrompt: string | null;`

3. **Add extractionPrompt to CreateSourceRequest**
   - File: `frontend/src/types/index.ts`
   - Add field: `extractionPrompt?: string;`

4. **Add extractionPrompt to UpdateSourceRequest**
   - File: `frontend/src/types/index.ts`
   - Add field: `extractionPrompt?: string;`

---

## Phase 7: Frontend Add Source Dialog

**Goal:** Add WEB source creation UI.

**Verification:** Can create WEB source via UI, appears in source list.

### Tasks

1. **Add WEB toggle button**
   - File: `frontend/src/components/sources/AddSourceDialog.tsx`
   - Import `Globe` from lucide-react
   - Add third button after EMAIL button with Globe icon and "Web Page" label

2. **Add webSchema validation**
   - Add schema requiring url and extractionPrompt
   - Both must be non-empty strings, url must be valid URL

3. **Update form state**
   - Add `extractionPrompt: ''` to initial formData state

4. **Add extractionPrompt textarea field**
   - Show only when sourceType === 'WEB'
   - Use Textarea component from shadcn/ui
   - Label: "Extraction Prompt *"
   - Placeholder: "Extract news items about AI and machine learning. Return title, summary, and link for each item."
   - Show validation error if present

5. **Add URL field for WEB type**
   - Show URL input when sourceType === 'WEB' (same as RSS)

6. **Update handleSubmit**
   - Add validation branch for WEB type using webSchema
   - Include extractionPrompt in sourceData when type is WEB

7. **Update dialog description**
   - Add WEB description: "Monitor a web page and extract news items using AI."

8. **Update form reset**
   - Include extractionPrompt in reset: `setFormData({ url: '', name: '', tags: '', extractionPrompt: '' })`

---

## Phase 8: Frontend Source Display

**Goal:** Display WEB sources correctly in list and detail views.

**Verification:** WEB sources show Globe icon and extraction prompt in UI.

### Tasks

1. **Update SourceCard**
   - File: `frontend/src/components/sources/SourceCard.tsx`
   - Import `Globe` from lucide-react
   - Add condition: WEB → Globe icon, EMAIL → Mail icon, default → Rss icon

2. **Update SourceDetailPage**
   - File: `frontend/src/pages/SourceDetailPage.tsx`
   - Import `Globe` from lucide-react
   - Add WEB icon condition (same as RSS for URL display)
   - Show extractionPrompt section when source.type === 'WEB'
   - Enable refresh button for WEB sources (same as RSS)

3. **Update SourceForm for editing**
   - File: `frontend/src/components/sources/SourceForm.tsx`
   - Add extractionPrompt Textarea field (shown when source.type === 'WEB')
   - Include extractionPrompt in form submission

---

## Phase 9: End-to-End Testing

**Goal:** Verify complete flow from creation to item extraction.

**Verification:** Create WEB source → manual refresh → items appear in list.

### Tasks

1. **Manual test: Create WEB source**
   - Start backend and frontend locally
   - Create a WEB source pointing to a real page (e.g., https://news.ycombinator.com)
   - Extraction prompt: "Extract the top 10 stories. For each, provide title, a brief description, and the link."
   - Verify source appears in list with Globe icon

2. **Manual test: Trigger refresh**
   - Click refresh button on source detail page
   - Wait for fetch to complete (check fetchStatus)
   - Verify items appear in source's news list

3. **Manual test: Verify deduplication**
   - Trigger another refresh
   - Verify no duplicate items created (same GUIDs)

4. **Manual test: Edit extraction prompt**
   - Edit source and change extraction prompt
   - Verify change persists after save
