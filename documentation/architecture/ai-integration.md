# AI Integration

Documentation for AI-powered features including the abstraction layer, structured output validation, and usage tracking.

## Overview

Morning Deck uses AI for several core features:
- **Article enrichment**: Generating summaries, extracting topics, entities, and sentiment
- **Relevance scoring**: Rating articles against user briefing criteria (0-100)
- **Content extraction**: Parsing newsletters and web pages into news items
- **Report generation**: Creating email subjects and summaries for daily reports

## Architecture

```
┌──────────────────────────────────────────────────────────────────────────┐
│                             Call Sites                                    │
│  ProcessingWorker │ EmailIngestionListener │ WebFetcher │ ReportEmail... │
└──────────────────────────────────────────────────────────────────────────┘
                               │
                               ▼
                    ┌───────────────────────┐
                    │   AiUsageContext      │  ThreadLocal user context
                    │   (set userId)        │
                    └───────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                    TrackedAiService (Decorator, @Primary)                 │
│  - Captures timing (duration_ms)                                          │
│  - Resolves user from AiUsageContext                                      │
│  - Delegates to SpringAiService                                           │
│  - Extracts token usage from ChatResponse                                 │
│  - Logs to ApiUsageLogService (async, non-blocking)                       │
└──────────────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                         SpringAiService                                   │
│  - Uses Spring AI ChatClient with OpenAI                                  │
│  - Generates JSON Schema dynamically from Java records                    │
│  - Enforces structured outputs via ResponseFormat.JSON_SCHEMA             │
│  - Parses responses with Jackson ObjectMapper                             │
└──────────────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                    OpenAI API (via Spring AI)                             │
└──────────────────────────────────────────────────────────────────────────┘
```

## Key Files

| File | Purpose |
|------|---------|
| `provider/ai/AiService.java` | Interface defining all AI operations |
| `provider/ai/SpringAiService.java` | OpenAI implementation with JSON Schema validation |
| `provider/ai/TrackedAiService.java` | Decorator that tracks usage metrics |
| `provider/ai/MockAiService.java` | Mock implementation for testing |
| `provider/ai/AiUsageContext.java` | ThreadLocal holder for user attribution |
| `provider/ai/AiFeature.java` | Enum for feature attribution |
| `provider/ai/AiCallResult.java` | Internal wrapper for result + usage metadata |
| `core/service/ApiUsageLogService.java` | Async service for persisting usage logs |
| `core/model/ApiUsageLog.java` | JPA entity for usage records |

## Service Interface

The `AiService` interface (`provider/ai/AiService.java:15`) defines all AI operations:

```java
public interface AiService {
    EnrichmentResult enrich(String title, String content);
    ScoreResult score(String title, String summary, String briefingCriteria);
    EnrichmentWithScoreResult enrichWithScore(String title, String content, String briefingCriteria);
    EnrichmentWithScoreResult enrichWithScore(String title, String content, String webContent, String briefingCriteria);
    List<ExtractedNewsItem> extractFromEmail(String subject, String content);
    List<ExtractedWebItem> extractFromWeb(String pageContent, String extractionPrompt);
    ReportEmailContent generateReportEmailContent(String briefingName, String briefingDescription, String items);
}
```

## JSON Schema Validation

The system uses OpenAI's structured outputs feature to guarantee valid JSON responses. This is implemented in `SpringAiService.java:228-283`.

### Schema Generation

JSON schemas are generated dynamically from Java record classes using Jackson:

```java
private String generateJsonSchema(Class<?> type) {
    JsonSchemaGenerator schemaGen = new JsonSchemaGenerator(objectMapper);
    JsonSchema schema = schemaGen.generateSchema(type);
    String schemaJson = objectMapper.writeValueAsString(schema);

    // Post-process for OpenAI compliance
    JsonNode schemaNode = objectMapper.readTree(schemaJson);
    addAdditionalPropertiesFalse(schemaNode);

    return objectMapper.writeValueAsString(schemaNode);
}
```

### Schema Post-Processing

OpenAI structured outputs require strict schemas. The `addAdditionalPropertiesFalse()` method (`SpringAiService.java:248-271`) recursively modifies schemas to add:
- `"additionalProperties": false` on all object types
- `"required"` array containing all property names

This prevents the LLM from returning extra fields not defined in the schema.

### Response Models

All AI responses are deserialized into Java records in `provider/ai/model/`:

| Model | Fields | Purpose |
|-------|--------|---------|
| `EnrichmentResult` | summary, topics, entities, sentiment | Basic article enrichment |
| `ScoreResult` | score (0-100), reasoning | Relevance scoring |
| `EnrichmentWithScoreResult` | All enrichment fields + score, scoreReasoning | Combined operation |
| `ExtractedNewsItem` | title, summary, url | Extracted from newsletters |
| `ExtractedWebItem` | title, content, link | Extracted from web pages |
| `ReportEmailContent` | subject, summary | Generated report email |
| `EntitiesResult` | people[], companies[], technologies[] | Nested entity extraction |

### Wrapper Records for Lists

OpenAI requires a top-level object in JSON Schema, so lists are wrapped:
- `ExtractedNewsItemList` wraps `List<ExtractedNewsItem>`
- `ExtractedWebItemList` wraps `List<ExtractedWebItem>`

### Schema Application

Schemas are applied via `OpenAiChatOptions` with `ResponseFormat.JSON_SCHEMA`:

```java
private OpenAiChatOptions jsonSchemaOptions(Class<?> type) {
    return OpenAiChatOptions.builder()
            .responseFormat(ResponseFormat.builder()
                    .type(ResponseFormat.Type.JSON_SCHEMA)
                    .jsonSchema(generateJsonSchema(type))
                    .build())
            .build();
}
```

## Prompt Templates

Prompts are stored as StringTemplate files in `src/main/resources/prompts/`:

| Template | Variables | Purpose |
|----------|-----------|---------|
| `enrich.st` | title, content | Basic enrichment |
| `score-relevance.st` | title, summary, briefingCriteria | Relevance scoring |
| `enrich-with-score.st` | title, content, briefingCriteria | Combined operation |
| `email-extract.st` | subject, content | Newsletter extraction |
| `web-extract.st` | pageContent, extractionPrompt | Web page extraction |
| `report-email.st` | briefingName, briefingDescription, items | Report generation |

### Content Truncation

Content is truncated to prevent token limit issues:
- Enrichment: 4000 characters
- Email/report extraction: 8000 characters
- Web content: 100000 characters

## Token Usage Tracking

Every AI call is tracked with full metadata for cost analysis and auditing.

### Decorator Pattern

`TrackedAiService` (`provider/ai/TrackedAiService.java:35`) wraps `SpringAiService` and is marked `@Primary` so it's injected by default:

```java
private <T> T trackCall(AiFeature feature, Supplier<AiCallResult<T>> call) {
    UUID userId = AiUsageContext.getUserId();
    long startTime = System.currentTimeMillis();
    boolean success = true;
    String errorMessage = null;
    Usage usage = null;
    String model = null;

    try {
        AiCallResult<T> result = call.get();
        usage = result.usage();
        model = result.model();
        return result.result();
    } catch (Exception e) {
        success = false;
        errorMessage = e.getMessage();
        throw e;
    } finally {
        long durationMs = System.currentTimeMillis() - startTime;
        usageLogService.logAsync(userId, feature, model, usage, success, errorMessage, durationMs);
    }
}
```

### User Context

The `AiUsageContext` class (`provider/ai/AiUsageContext.java:19`) uses ThreadLocal to track which user triggered an AI call:

```java
public final class AiUsageContext {
    private static final ThreadLocal<UUID> currentUserId = new ThreadLocal<>();

    public static void setUserId(UUID userId) { currentUserId.set(userId); }
    public static UUID getUserId() { return currentUserId.get(); }
    public static void clear() { currentUserId.remove(); }
}
```

**Usage pattern** (required at all call sites):

```java
try {
    AiUsageContext.setUserId(userId);
    aiService.enrichWithScore(...);
} finally {
    AiUsageContext.clear();
}
```

### Feature Attribution

The `AiFeature` enum (`provider/ai/AiFeature.java:7`) tags each call with its purpose:

| Feature | Method | Description |
|---------|--------|-------------|
| `ENRICH` | `enrich()` | Article summarization and tagging |
| `SCORE` | `score()` | Relevance scoring against briefing |
| `ENRICH_SCORE` | `enrichWithScore()` | Combined enrichment + scoring |
| `EMAIL_EXTRACT` | `extractFromEmail()` | Newsletter parsing |
| `WEB_EXTRACT` | `extractFromWeb()` | Web page extraction |
| `REPORT_GEN` | `generateReportEmailContent()` | Report email generation |

### Persisted Metrics

The `ApiUsageLog` entity (`core/model/ApiUsageLog.java`) stores:

| Field | Type | Description |
|-------|------|-------------|
| `user_id` | UUID (FK) | User who triggered the call |
| `feature_key` | Enum | Which AI feature was used |
| `model` | String | Model identifier (e.g., gpt-5-nano) |
| `input_tokens` | Long | Prompt tokens sent |
| `output_tokens` | Long | Completion tokens received |
| `total_tokens` | Long | Combined token count |
| `success` | Boolean | Whether the call succeeded |
| `error_message` | String | Error details if failed (truncated to 1024 chars) |
| `duration_ms` | Long | Round-trip latency |
| `created_at` | Timestamp | When the call occurred |

Database indexes on `user_id`, `feature_key`, and `created_at` enable efficient queries.

### Async Logging

`ApiUsageLogService.logAsync()` (`core/service/ApiUsageLogService.java:40`) runs asynchronously via `@Async` to avoid blocking the main request flow. Logging failures are swallowed to prevent side effects on the main operation.

## Configuration

### Production (`application.properties`)

```properties
spring.ai.openai.chat.enabled=true
spring.ai.openai.chat.options.model=gpt-5-nano
spring.ai.openai.api-key=${OPENAI_API_KEY}
application.ai.provider=openai
```

### Local Development (`application-local.properties`)

```properties
application.ai.provider=openai
spring.ai.openai.chat.enabled=true
spring.ai.openai.chat.options.model=openai/gpt-oss-120b
spring.ai.openai.api-key=<groq-api-key>
spring.ai.openai.base-url=https://api.groq.com/openai
```

Local development uses Groq-hosted open-source models for free API access.

### Mock Provider

Set `application.ai.provider=mock` to use `MockAiService` which returns deterministic responses without external calls. Used for testing.

## Error Handling

- Exceptions are wrapped in `AiProcessingException` (`core/exception/AiProcessingException.java`)
- Failed calls are logged with `success=false` and the error message
- Web fetching failures don't block enrichment (graceful degradation)
- Missing token usage metadata is logged as a warning but doesn't fail the operation

## Adding New AI Operations

1. **Define response model** as a Java record in `provider/ai/model/`
2. **Create prompt template** in `src/main/resources/prompts/`
3. **Add method to AiService** interface
4. **Implement in SpringAiService** with `*Tracked()` variant returning `AiCallResult<T>`
5. **Add to AiFeature** enum if it's a new feature category
6. **Wrap in TrackedAiService** to delegate to the tracked variant
7. **Set AiUsageContext** at all call sites before invoking

## Related Documentation

- [Credits](../domain/credits.md) - Credit costs for AI operations
- [News Items](../domain/news-items.md) - How AI enriches news items
- [Sources](../domain/sources.md) - AI extraction for Web and Email sources
