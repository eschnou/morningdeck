# Fetch Web Content - Implementation Plan

## Phase 1: Foundation - Data Model & Interface

**Goal:** Add database field and create pluggable interface for web content fetching.

**Verification:** Run `mvn test` - all existing tests pass; new migration applied.

### Tasks

#### 1.1 Add Readability4J dependency
- Add to `pom.xml`:
  ```xml
  <dependency>
      <groupId>net.dankito.readability4j</groupId>
      <artifactId>readability4j</artifactId>
      <version>1.0.8</version>
  </dependency>
  ```
- Run `mvn compile` to verify dependency resolution

#### 1.2 Create database migration
- File: `src/main/resources/db/migrations/V12__add_web_content_to_news_items.sql`
- Content:
  ```sql
  ALTER TABLE news_items ADD COLUMN web_content TEXT;
  ```

#### 1.3 Update NewsItem entity
- File: `core/model/NewsItem.java`
- Add field:
  ```java
  @Column(name = "web_content", columnDefinition = "TEXT")
  private String webContent;
  ```

#### 1.4 Create WebContentFetcher interface
- File: `provider/webfetch/WebContentFetcher.java`
- Interface with single method: `Optional<String> fetch(String url)`

#### 1.5 Create NoOpWebFetcher
- File: `provider/webfetch/NoOpWebFetcher.java`
- Annotate with `@ConditionalOnProperty(prefix = "application.web-fetch", name = "enabled", havingValue = "false")`
- Returns `Optional.empty()` always
- **Unit test:** `NoOpWebFetcherTest` - verify returns empty for any URL

---

## Phase 2: HttpClient Implementation

**Goal:** Implement web fetching with content extraction using Readability4J.

**Verification:** Run `mvn test` - new unit tests pass for HttpClientWebFetcher.

### Tasks

#### 2.1 Add configuration properties
- File: `src/main/resources/application.properties`
- Add:
  ```properties
  # Web Content Fetching
  application.web-fetch.enabled=true
  application.web-fetch.timeout-seconds=15
  application.web-fetch.user-agent=DayBrief/1.0
  ```
- File: `src/main/resources/application-local.properties`
- Add same properties

#### 2.2 Create HttpClientWebFetcher
- File: `provider/webfetch/HttpClientWebFetcher.java`
- Annotate with `@ConditionalOnProperty(prefix = "application.web-fetch", name = "enabled", havingValue = "true", matchIfMissing = true)`
- Inject `HtmlToMarkdownConverter`
- Implement:
  - `isValidUrl()` - validate HTTP/HTTPS, reject private IPs (SSRF prevention)
  - `fetchHtml()` - use Java HttpClient with timeout
  - `extractArticle()` - use Readability4J
  - `fetch()` - orchestrate and return markdown
- **Unit tests:** `HttpClientWebFetcherTest`
  - `shouldReturnEmptyForNullUrl`
  - `shouldReturnEmptyForMailtoUrl`
  - `shouldReturnEmptyForPrivateIp`
  - `shouldReturnEmptyForLocalhost`
  - `shouldExtractContentFromValidHtml` (mock HTTP)

#### 2.3 Create URL validation utility
- In `HttpClientWebFetcher`:
  - Check protocol is http/https
  - Parse host, reject: `localhost`, `127.0.0.1`, `10.*`, `172.16-31.*`, `192.168.*`, `169.254.*`
  - Log rejected URLs at DEBUG level

---

## Phase 3: Processing Integration

**Goal:** Integrate web fetching into news item processing pipeline.

**Verification:** Run `mvn test` - ProcessingWorker tests pass with mocked WebContentFetcher.

### Tasks

#### 3.1 Update AiService interface
- File: `provider/ai/AiService.java`
- Add overloaded method:
  ```java
  EnrichmentWithScoreResult enrichWithScore(String title, String content, String webContent, String briefingCriteria);
  ```

#### 3.2 Update SpringAiService
- File: `provider/ai/SpringAiService.java`
- Implement new method with `buildEffectiveContent()` helper:
  - If webContent null/blank, use original content
  - If original < 500 chars, combine: "Original snippet:\n{original}\n\nFull article:\n{webContent}"
  - Otherwise use webContent only
- Delegate existing 3-arg method to new 4-arg method with null webContent

#### 3.3 Update MockAiService
- File: `provider/ai/MockAiService.java`
- Add overloaded method (delegate to existing or return mock)

#### 3.4 Modify ProcessingWorker
- File: `core/queue/ProcessingWorker.java`
- Inject `WebContentFetcher`
- Add `shouldFetchWebContent(NewsItem)`:
  - Return false if link null or not http/https
  - Return false if cleanContent > 2000 chars
  - Return true otherwise
- In `doProcess()`:
  - Call `shouldFetchWebContent()`
  - If true, call `webContentFetcher.fetch(link)`
  - Store result in `item.setWebContent()`
  - Pass webContent to `aiService.enrichWithScore()`
- **Unit tests:** `ProcessingWorkerTest` (mock WebContentFetcher, AiService, Repository)
  - `shouldFetchWebContentWhenLinkPresent`
  - `shouldSkipFetchWhenContentOver2000Chars`
  - `shouldSkipFetchForNonHttpLink`
  - `shouldProceedWhenFetchFails`
  - `shouldStoreWebContentWhenFetched`

---

## Phase 4: DTO Content Selection

**Goal:** Return best available content to frontend via DTO mapping.

**Verification:** Run `mvn test` - NewsItemService tests verify content selection.

### Tasks

#### 4.1 Update NewsItemService
- File: `core/service/NewsItemService.java`
- Add private method:
  ```java
  private String selectBestContent(NewsItem item) {
      if (item.getWebContent() != null && !item.getWebContent().isBlank()) {
          return item.getWebContent();
      }
      return item.getCleanContent();
  }
  ```
- Update `mapToDTO()` to use `selectBestContent(item)` instead of `item.getCleanContent()`
- **Unit tests:** `NewsItemServiceTest`
  - `shouldReturnWebContentWhenAvailable`
  - `shouldFallbackToCleanContentWhenWebContentNull`
  - `shouldFallbackToCleanContentWhenWebContentBlank`

---

## Phase 5: Integration Testing

**Goal:** Verify end-to-end web fetching with real HTTP calls (mocked server).

**Verification:** Run `mvn verify` - integration tests pass.

### Tasks

#### 5.1 Create WebFetchIT integration test
- File: `src/test/java/be/transcode/daybrief/server/provider/webfetch/WebFetchIT.java`
- Use WireMock to mock HTTP responses
- Tests:
  - `shouldFetchAndExtractArticleContent` - return HTML, verify markdown output
  - `shouldHandleRedirects` - 301/302 response
  - `shouldHandleTimeouts` - delayed response
  - `shouldHandle404` - return empty
  - `shouldHandle500` - return empty

#### 5.2 Manual verification
- Start application locally: `mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"`
- Add RSS source with truncated content (e.g., Hacker News RSS)
- Wait for processing
- Verify in logs: web content fetch attempts
- Check database: `webContent` field populated
- View news item in UI: full article content displayed

---

## File Summary

| Phase | File | Action |
|-------|------|--------|
| 1 | `pom.xml` | Add Readability4J dependency |
| 1 | `db/migrations/V12__add_web_content_to_news_items.sql` | Create |
| 1 | `core/model/NewsItem.java` | Add webContent field |
| 1 | `provider/webfetch/WebContentFetcher.java` | Create interface |
| 1 | `provider/webfetch/NoOpWebFetcher.java` | Create |
| 2 | `application.properties` | Add web-fetch config |
| 2 | `application-local.properties` | Add web-fetch config |
| 2 | `provider/webfetch/HttpClientWebFetcher.java` | Create |
| 3 | `provider/ai/AiService.java` | Add overloaded method |
| 3 | `provider/ai/SpringAiService.java` | Implement new method |
| 3 | `provider/ai/MockAiService.java` | Add overloaded method |
| 3 | `core/queue/ProcessingWorker.java` | Add web fetch logic |
| 4 | `core/service/NewsItemService.java` | Add selectBestContent |
| 5 | `provider/webfetch/WebFetchIT.java` | Create integration test |

## Test Summary

| Phase | Test File | Tests |
|-------|-----------|-------|
| 1 | `NoOpWebFetcherTest.java` | 1 test |
| 2 | `HttpClientWebFetcherTest.java` | 5 tests |
| 3 | `ProcessingWorkerTest.java` | 5 tests |
| 4 | `NewsItemServiceTest.java` | 3 tests |
| 5 | `WebFetchIT.java` | 5 tests |
