package be.transcode.morningdeck.server.provider.ai;

import be.transcode.morningdeck.server.core.exception.AiProcessingException;
import be.transcode.morningdeck.server.provider.ai.model.EnrichmentResult;
import be.transcode.morningdeck.server.provider.ai.model.EnrichmentWithScoreResult;
import be.transcode.morningdeck.server.provider.ai.model.ExtractedNewsItem;
import be.transcode.morningdeck.server.provider.ai.model.ExtractedNewsItemList;
import be.transcode.morningdeck.server.provider.ai.model.ExtractedWebItem;
import be.transcode.morningdeck.server.provider.ai.model.ExtractedWebItemList;
import be.transcode.morningdeck.server.provider.ai.model.ReportEmailContent;
import be.transcode.morningdeck.server.provider.ai.model.ScoreResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.JsonSchemaGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import org.springframework.lang.Nullable;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Spring AI implementation of the AiService using OpenAI.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "application.ai", name = "provider", havingValue = "openai")
public class SpringAiService implements AiService {

    private final ChatClient liteClient;
    private final ChatClient heavyClient;
    private final ObjectMapper objectMapper;

    @Value("classpath:prompts/enrich.st")
    private Resource enrichPromptResource;

    @Value("classpath:prompts/score-relevance.st")
    private Resource scoreRelevancePromptResource;

    @Value("classpath:prompts/enrich-with-score.st")
    private Resource enrichWithScorePromptResource;

    @Value("classpath:prompts/email-extract.st")
    private Resource emailExtractPromptResource;

    @Value("classpath:prompts/web-extract.st")
    private Resource webExtractPromptResource;

    @Value("classpath:prompts/report-email.st")
    private Resource reportEmailPromptResource;

    public SpringAiService(
            @Qualifier("liteClient") ChatClient liteClient,
            @Qualifier("heavyClient") ChatClient heavyClient,
            ObjectMapper objectMapper) {
        this.liteClient = liteClient;
        this.heavyClient = heavyClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Selects the appropriate ChatClient based on the feature's model tier.
     */
    private ChatClient getClient(AiFeature feature) {
        return switch (feature.getTier()) {
            case LITE -> liteClient;
            case HEAVY -> heavyClient;
        };
    }

    @Override
    public EnrichmentResult enrich(String title, String content) {
        return enrichTracked(title, content).result();
    }

    @Override
    public ScoreResult score(String title, String summary, String briefingCriteria) {
        return scoreTracked(title, summary, briefingCriteria).result();
    }

    @Override
    public EnrichmentWithScoreResult enrichWithScore(String title, String content, String briefingCriteria) {
        return enrichWithScoreTracked(title, content, null, briefingCriteria).result();
    }

    @Override
    public EnrichmentWithScoreResult enrichWithScore(String title, String content, String webContent, String briefingCriteria) {
        return enrichWithScoreTracked(title, content, webContent, briefingCriteria).result();
    }

    @Override
    public List<ExtractedNewsItem> extractFromEmail(String subject, String content) {
        return extractFromEmailTracked(subject, content).result();
    }

    @Override
    public List<ExtractedWebItem> extractFromWeb(String pageContent, String extractionPrompt) {
        return extractFromWebTracked(pageContent, extractionPrompt).result();
    }

    @Override
    public ReportEmailContent generateReportEmailContent(String briefingName, String briefingDescription, String items) {
        return generateReportEmailContentTracked(briefingName, briefingDescription, items).result();
    }

    // ========== Tracked variants that expose usage metadata ==========

    public AiCallResult<EnrichmentResult> enrichTracked(String title, String content) {
        log.debug("Enriching article: {}", title);
        try {
            ChatResponse response = getClient(AiFeature.ENRICH).prompt()
                    .user(u -> u.text(loadPrompt(enrichPromptResource))
                            .param("title", title)
                            .param("content", truncate(content, 4000)))
                    .options(jsonSchemaOptions(EnrichmentResult.class))
                    .call()
                    .chatResponse();

            EnrichmentResult entity = parseEntity(response, EnrichmentResult.class);
            return buildResult(entity, response);
        } catch (Exception e) {
            log.error("Failed to enrich article: {}", e.getMessage(), e);
            throw new AiProcessingException("Failed to enrich article: " + e.getMessage());
        }
    }

    public AiCallResult<ScoreResult> scoreTracked(String title, String summary, String briefingCriteria) {
        log.debug("Scoring article: {}", title);
        try {
            ChatResponse response = getClient(AiFeature.SCORE).prompt()
                    .user(u -> u.text(loadPrompt(scoreRelevancePromptResource))
                            .param("title", title)
                            .param("summary", summary)
                            .param("briefingCriteria", briefingCriteria))
                    .options(jsonSchemaOptions(ScoreResult.class))
                    .call()
                    .chatResponse();

            ScoreResult entity = parseEntity(response, ScoreResult.class);
            return buildResult(entity, response);
        } catch (Exception e) {
            log.error("Failed to score article: {}", e.getMessage(), e);
            throw new AiProcessingException("Failed to score article: " + e.getMessage());
        }
    }

    public AiCallResult<EnrichmentWithScoreResult> enrichWithScoreTracked(String title, String content, String webContent, String briefingCriteria) {
        log.debug("Enriching and scoring article: {}", title);
        try {
            String effectiveContent = buildEffectiveContent(content, webContent);
            ChatResponse response = getClient(AiFeature.ENRICH_SCORE).prompt()
                    .user(u -> u.text(loadPrompt(enrichWithScorePromptResource))
                            .param("title", title)
                            .param("content", truncate(effectiveContent, 4000))
                            .param("briefingCriteria", briefingCriteria))
                    .options(jsonSchemaOptions(EnrichmentWithScoreResult.class))
                    .call()
                    .chatResponse();

            EnrichmentWithScoreResult entity = parseEntity(response, EnrichmentWithScoreResult.class);
            return buildResult(entity, response);
        } catch (Exception e) {
            log.error("Failed to enrich and score article: {}", e.getMessage(), e);
            throw new AiProcessingException("Failed to enrich and score article: " + e.getMessage());
        }
    }

    public AiCallResult<List<ExtractedNewsItem>> extractFromEmailTracked(String subject, String content) {
        log.debug("Extracting news items from email: {}", subject);
        try {
            ChatResponse response = getClient(AiFeature.EMAIL_EXTRACT).prompt()
                    .user(u -> u.text(loadPrompt(emailExtractPromptResource))
                            .param("subject", subject)
                            .param("content", truncate(content, 8000)))
                    .options(jsonSchemaOptions(ExtractedNewsItemList.class))
                    .call()
                    .chatResponse();

            ExtractedNewsItemList wrapper = parseEntity(response, ExtractedNewsItemList.class);
            return buildResult(wrapper.items(), response);
        } catch (Exception e) {
            log.error("Failed to extract from email: {}", e.getMessage(), e);
            throw new AiProcessingException("Failed to extract news items from email: " + e.getMessage());
        }
    }

    public AiCallResult<List<ExtractedWebItem>> extractFromWebTracked(String pageContent, String extractionPrompt) {
        log.debug("Extracting news items from web page with prompt: {}", truncate(extractionPrompt, 100));
        try {
            ChatResponse response = getClient(AiFeature.WEB_EXTRACT).prompt()
                    .user(u -> u.text(loadPrompt(webExtractPromptResource))
                            .param("extractionPrompt", extractionPrompt)
                            .param("pageContent", truncate(pageContent, 100000)))
                    .options(jsonSchemaOptions(ExtractedWebItemList.class))
                    .call()
                    .chatResponse();

            ExtractedWebItemList wrapper = parseEntity(response, ExtractedWebItemList.class);
            return buildResult(wrapper.items(), response);
        } catch (Exception e) {
            log.error("Failed to extract from web page: {}", e.getMessage(), e);
            throw new AiProcessingException("Failed to extract news items from web page: " + e.getMessage());
        }
    }

    public AiCallResult<ReportEmailContent> generateReportEmailContentTracked(String briefingName, String briefingDescription, String items) {
        log.debug("Generating email content for briefing: {}", briefingName);
        try {
            ChatResponse response = getClient(AiFeature.REPORT_GEN).prompt()
                    .user(u -> u.text(loadPrompt(reportEmailPromptResource))
                            .param("briefingName", briefingName)
                            .param("briefingDescription", briefingDescription != null ? briefingDescription : "")
                            .param("items", truncate(items, 8000)))
                    .options(jsonSchemaOptions(ReportEmailContent.class))
                    .call()
                    .chatResponse();

            ReportEmailContent entity = parseEntity(response, ReportEmailContent.class);
            return buildResult(entity, response);
        } catch (Exception e) {
            log.error("Failed to generate report email content: {}", e.getMessage(), e);
            throw new AiProcessingException("Failed to generate report email content: " + e.getMessage());
        }
    }

    // ========== Helper methods ==========

    /**
     * Generates a JSON schema string from the given class using Jackson.
     * Adds additionalProperties: false to all objects as required by OpenAI structured outputs.
     * Handles @Nullable annotated fields by allowing null values.
     */
    private String generateJsonSchema(Class<?> type) {
        try {
            JsonSchemaGenerator schemaGen = new JsonSchemaGenerator(objectMapper);
            JsonSchema schema = schemaGen.generateSchema(type);
            String schemaJson = objectMapper.writeValueAsString(schema);

            // Collect nullable fields from the class hierarchy
            Set<String> nullableFields = collectNullableFields(type);

            // Parse and add additionalProperties: false to all objects
            JsonNode schemaNode = objectMapper.readTree(schemaJson);
            addAdditionalPropertiesFalse(schemaNode, nullableFields);

            return objectMapper.writeValueAsString(schemaNode);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to generate JSON schema for " + type.getName(), e);
        }
    }

    /**
     * Recursively collects field names annotated with @Nullable from a class and its nested types.
     */
    private Set<String> collectNullableFields(Class<?> type) {
        Set<String> nullableFields = new HashSet<>();
        collectNullableFieldsRecursive(type, nullableFields, new HashSet<>());
        return nullableFields;
    }

    private void collectNullableFieldsRecursive(Class<?> type, Set<String> nullableFields, Set<Class<?>> visited) {
        if (type == null || visited.contains(type) || type.isPrimitive() || type.getName().startsWith("java.lang")) {
            return;
        }
        visited.add(type);

        // Handle records
        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                if (component.isAnnotationPresent(Nullable.class)) {
                    nullableFields.add(component.getName());
                }
                // Recurse into component types (for nested records/objects)
                collectNullableFieldsRecursive(component.getType(), nullableFields, visited);
                // Handle generic types like List<ExtractedNewsItem>
                if (component.getGenericType() instanceof java.lang.reflect.ParameterizedType pt) {
                    for (var arg : pt.getActualTypeArguments()) {
                        if (arg instanceof Class<?> argClass) {
                            collectNullableFieldsRecursive(argClass, nullableFields, visited);
                        }
                    }
                }
            }
        }

        // Handle regular classes
        for (var field : type.getDeclaredFields()) {
            if (field.isAnnotationPresent(Nullable.class)) {
                nullableFields.add(field.getName());
            }
            collectNullableFieldsRecursive(field.getType(), nullableFields, visited);
        }
    }

    /**
     * Recursively adds additionalProperties: false and required array to all object types.
     * Also handles @Nullable fields by allowing null values in their type.
     * Both additionalProperties: false and required array are required by OpenAI's structured outputs.
     */
    private void addAdditionalPropertiesFalse(JsonNode node, Set<String> nullableFields) {
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;

            // If this is an object type schema with properties
            JsonNode typeNode = objectNode.get("type");
            JsonNode propertiesNode = objectNode.get("properties");
            if (typeNode != null && "object".equals(typeNode.asText())) {
                objectNode.put("additionalProperties", false);

                // Process properties and handle nullable fields
                if (propertiesNode != null && propertiesNode.isObject()) {
                    var requiredArray = objectMapper.createArrayNode();
                    propertiesNode.fieldNames().forEachRemaining(fieldName -> {
                        requiredArray.add(fieldName);
                        // Make nullable fields allow null in their type
                        if (nullableFields.contains(fieldName)) {
                            JsonNode propNode = propertiesNode.get(fieldName);
                            if (propNode.isObject()) {
                                ObjectNode propObjectNode = (ObjectNode) propNode;
                                JsonNode propTypeNode = propObjectNode.get("type");
                                if (propTypeNode != null && propTypeNode.isTextual()) {
                                    // Change "type": "string" to "type": ["string", "null"]
                                    var typeArray = objectMapper.createArrayNode();
                                    typeArray.add(propTypeNode.asText());
                                    typeArray.add("null");
                                    propObjectNode.set("type", typeArray);
                                }
                            }
                        }
                    });
                    objectNode.set("required", requiredArray);
                }
            }

            // Recurse into all child nodes
            node.fields().forEachRemaining(entry -> addAdditionalPropertiesFalse(entry.getValue(), nullableFields));
        } else if (node.isArray()) {
            node.forEach(child -> addAdditionalPropertiesFalse(child, nullableFields));
        }
    }

    /**
     * Creates OpenAI chat options with JSON_SCHEMA response format.
     */
    private OpenAiChatOptions jsonSchemaOptions(Class<?> type) {
        return OpenAiChatOptions.builder()
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormat.Type.JSON_SCHEMA)
                        .jsonSchema(generateJsonSchema(type))
                        .build())
                .build();
    }

    /**
     * Combines original content with web content for optimal LLM input.
     */
    private String buildEffectiveContent(String original, String webContent) {
        if (webContent == null || webContent.isBlank()) {
            return original;
        }
        if (original != null && !original.isBlank() && original.length() < 500) {
            return "Original snippet:\n" + original + "\n\nFull article:\n" + webContent;
        }
        return webContent;
    }

    private <T> T parseEntity(ChatResponse response, Class<T> type) {
        String content = extractContent(response);
        try {
            return objectMapper.readValue(content, type);
        } catch (JsonProcessingException e) {
            throw new AiProcessingException("Failed to parse AI response: " + e.getMessage());
        }
    }

    private String extractContent(ChatResponse response) {
        if (response.getResults().isEmpty()) {
            throw new AiProcessingException("Empty AI response");
        }
        String content = response.getResults().get(0).getOutput().getText();
        if (content == null || content.isBlank()) {
            throw new AiProcessingException("Empty AI response content");
        }
        return content.trim();
    }

    private <T> AiCallResult<T> buildResult(T entity, ChatResponse response) {
        Usage usage = response.getMetadata() != null ? response.getMetadata().getUsage() : null;
        String model = response.getMetadata() != null ? response.getMetadata().getModel() : null;
        return new AiCallResult<>(entity, usage, model);
    }

    private String loadPrompt(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load prompt resource: " + resource.getFilename(), e);
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
