package be.transcode.morningdeck.server.provider.ai;

import be.transcode.morningdeck.server.provider.ai.model.EnrichmentResult;
import be.transcode.morningdeck.server.provider.ai.model.EnrichmentWithScoreResult;
import be.transcode.morningdeck.server.provider.ai.model.EntitiesResult;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.lang.Nullable;

import java.lang.reflect.RecordComponent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for JSON schema generation and parsing of AI model types.
 * These tests ensure that:
 * 1. JSON schemas are generated correctly from model classes
 * 2. JSON responses matching the schema can be parsed back to objects
 * 3. Wrapper types for lists work correctly
 */
@DisplayName("JSON Schema Generation and Parsing Tests")
class JsonSchemaTest {

    private ObjectMapper objectMapper;
    private JsonSchemaGenerator schemaGenerator;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        schemaGenerator = new JsonSchemaGenerator(objectMapper);
    }

    @Nested
    @DisplayName("Schema Generation Tests")
    class SchemaGenerationTests {

        @Test
        @DisplayName("Should generate schema for ScoreResult")
        void shouldGenerateSchemaForScoreResult() throws JsonProcessingException {
            JsonSchema schema = schemaGenerator.generateSchema(ScoreResult.class);
            String schemaJson = objectMapper.writeValueAsString(schema);
            JsonNode schemaNode = objectMapper.readTree(schemaJson);

            assertThat(schemaNode.get("type").asText()).isEqualTo("object");
            assertThat(schemaNode.has("properties")).isTrue();
            assertThat(schemaNode.get("properties").has("score")).isTrue();
            assertThat(schemaNode.get("properties").has("reasoning")).isTrue();
        }

        @Test
        @DisplayName("Should generate schema for EnrichmentResult with nested entities")
        void shouldGenerateSchemaForEnrichmentResult() throws JsonProcessingException {
            JsonSchema schema = schemaGenerator.generateSchema(EnrichmentResult.class);
            String schemaJson = objectMapper.writeValueAsString(schema);
            JsonNode schemaNode = objectMapper.readTree(schemaJson);

            assertThat(schemaNode.get("type").asText()).isEqualTo("object");
            JsonNode properties = schemaNode.get("properties");
            assertThat(properties.has("summary")).isTrue();
            assertThat(properties.has("topics")).isTrue();
            assertThat(properties.has("entities")).isTrue();
            assertThat(properties.has("sentiment")).isTrue();
        }

        @Test
        @DisplayName("Should generate schema for EnrichmentWithScoreResult")
        void shouldGenerateSchemaForEnrichmentWithScoreResult() throws JsonProcessingException {
            JsonSchema schema = schemaGenerator.generateSchema(EnrichmentWithScoreResult.class);
            String schemaJson = objectMapper.writeValueAsString(schema);
            JsonNode schemaNode = objectMapper.readTree(schemaJson);

            assertThat(schemaNode.get("type").asText()).isEqualTo("object");
            JsonNode properties = schemaNode.get("properties");
            assertThat(properties.has("summary")).isTrue();
            assertThat(properties.has("topics")).isTrue();
            assertThat(properties.has("entities")).isTrue();
            assertThat(properties.has("sentiment")).isTrue();
            assertThat(properties.has("score")).isTrue();
            assertThat(properties.has("scoreReasoning")).isTrue();
        }

        @Test
        @DisplayName("Should generate schema for ReportEmailContent")
        void shouldGenerateSchemaForReportEmailContent() throws JsonProcessingException {
            JsonSchema schema = schemaGenerator.generateSchema(ReportEmailContent.class);
            String schemaJson = objectMapper.writeValueAsString(schema);
            JsonNode schemaNode = objectMapper.readTree(schemaJson);

            assertThat(schemaNode.get("type").asText()).isEqualTo("object");
            JsonNode properties = schemaNode.get("properties");
            assertThat(properties.has("subject")).isTrue();
            assertThat(properties.has("summary")).isTrue();
        }

        @Test
        @DisplayName("Should generate schema for ExtractedNewsItemList wrapper")
        void shouldGenerateSchemaForExtractedNewsItemList() throws JsonProcessingException {
            JsonSchema schema = schemaGenerator.generateSchema(ExtractedNewsItemList.class);
            String schemaJson = objectMapper.writeValueAsString(schema);
            JsonNode schemaNode = objectMapper.readTree(schemaJson);

            assertThat(schemaNode.get("type").asText()).isEqualTo("object");
            assertThat(schemaNode.get("properties").has("items")).isTrue();
        }

        @Test
        @DisplayName("Should generate schema for ExtractedWebItemList wrapper")
        void shouldGenerateSchemaForExtractedWebItemList() throws JsonProcessingException {
            JsonSchema schema = schemaGenerator.generateSchema(ExtractedWebItemList.class);
            String schemaJson = objectMapper.writeValueAsString(schema);
            JsonNode schemaNode = objectMapper.readTree(schemaJson);

            assertThat(schemaNode.get("type").asText()).isEqualTo("object");
            assertThat(schemaNode.get("properties").has("items")).isTrue();
        }
    }

    @Nested
    @DisplayName("JSON Parsing Tests")
    class JsonParsingTests {

        @Test
        @DisplayName("Should parse ScoreResult from JSON")
        void shouldParseScoreResult() throws JsonProcessingException {
            String json = """
                {
                    "score": 85,
                    "reasoning": "Highly relevant to AI topics"
                }
                """;

            ScoreResult result = objectMapper.readValue(json, ScoreResult.class);

            assertThat(result.score()).isEqualTo(85);
            assertThat(result.reasoning()).isEqualTo("Highly relevant to AI topics");
        }

        @Test
        @DisplayName("Should parse EnrichmentResult from JSON")
        void shouldParseEnrichmentResult() throws JsonProcessingException {
            String json = """
                {
                    "summary": "Article about AI advancements",
                    "topics": ["AI", "Technology", "Research"],
                    "entities": {
                        "people": ["Sam Altman"],
                        "companies": ["OpenAI", "Anthropic"],
                        "technologies": ["GPT-4", "Claude"]
                    },
                    "sentiment": "positive"
                }
                """;

            EnrichmentResult result = objectMapper.readValue(json, EnrichmentResult.class);

            assertThat(result.summary()).isEqualTo("Article about AI advancements");
            assertThat(result.topics()).containsExactly("AI", "Technology", "Research");
            assertThat(result.entities().people()).containsExactly("Sam Altman");
            assertThat(result.entities().companies()).containsExactly("OpenAI", "Anthropic");
            assertThat(result.entities().technologies()).containsExactly("GPT-4", "Claude");
            assertThat(result.sentiment()).isEqualTo("positive");
        }

        @Test
        @DisplayName("Should parse EnrichmentWithScoreResult from JSON")
        void shouldParseEnrichmentWithScoreResult() throws JsonProcessingException {
            String json = """
                {
                    "summary": "Breaking news about technology",
                    "topics": ["Tech", "Innovation"],
                    "entities": {
                        "people": [],
                        "companies": ["Apple"],
                        "technologies": ["Vision Pro"]
                    },
                    "sentiment": "neutral",
                    "score": 72,
                    "scoreReasoning": "Relevant but not directly related to user interests"
                }
                """;

            EnrichmentWithScoreResult result = objectMapper.readValue(json, EnrichmentWithScoreResult.class);

            assertThat(result.summary()).isEqualTo("Breaking news about technology");
            assertThat(result.topics()).containsExactly("Tech", "Innovation");
            assertThat(result.score()).isEqualTo(72);
            assertThat(result.scoreReasoning()).isEqualTo("Relevant but not directly related to user interests");
        }

        @Test
        @DisplayName("Should parse ReportEmailContent from JSON")
        void shouldParseReportEmailContent() throws JsonProcessingException {
            String json = """
                {
                    "subject": "Your Daily Tech Briefing",
                    "summary": "Today's highlights include AI breakthroughs and market updates."
                }
                """;

            ReportEmailContent result = objectMapper.readValue(json, ReportEmailContent.class);

            assertThat(result.subject()).isEqualTo("Your Daily Tech Briefing");
            assertThat(result.summary()).isEqualTo("Today's highlights include AI breakthroughs and market updates.");
        }

        @Test
        @DisplayName("Should parse ExtractedNewsItemList wrapper from JSON")
        void shouldParseExtractedNewsItemList() throws JsonProcessingException {
            String json = """
                {
                    "items": [
                        {
                            "title": "AI Startup Raises $100M",
                            "summary": "A new AI company secures major funding.",
                            "url": "https://example.com/article1"
                        },
                        {
                            "title": "Tech Giants Report Earnings",
                            "summary": "Q4 results exceed expectations.",
                            "url": null
                        }
                    ]
                }
                """;

            ExtractedNewsItemList result = objectMapper.readValue(json, ExtractedNewsItemList.class);

            assertThat(result.items()).hasSize(2);
            assertThat(result.items().get(0).title()).isEqualTo("AI Startup Raises $100M");
            assertThat(result.items().get(0).url()).isEqualTo("https://example.com/article1");
            assertThat(result.items().get(1).title()).isEqualTo("Tech Giants Report Earnings");
            assertThat(result.items().get(1).url()).isNull();
        }

        @Test
        @DisplayName("Should parse ExtractedWebItemList wrapper from JSON")
        void shouldParseExtractedWebItemList() throws JsonProcessingException {
            String json = """
                {
                    "items": [
                        {
                            "title": "Article One",
                            "content": "Summary of article one.",
                            "link": "/articles/1"
                        },
                        {
                            "title": "Article Two",
                            "content": "Summary of article two.",
                            "link": "https://example.com/articles/2"
                        }
                    ]
                }
                """;

            ExtractedWebItemList result = objectMapper.readValue(json, ExtractedWebItemList.class);

            assertThat(result.items()).hasSize(2);
            assertThat(result.items().get(0).title()).isEqualTo("Article One");
            assertThat(result.items().get(0).link()).isEqualTo("/articles/1");
            assertThat(result.items().get(1).link()).isEqualTo("https://example.com/articles/2");
        }

        @Test
        @DisplayName("Should parse empty items list in wrapper")
        void shouldParseEmptyItemsList() throws JsonProcessingException {
            String json = """
                {
                    "items": []
                }
                """;

            ExtractedNewsItemList result = objectMapper.readValue(json, ExtractedNewsItemList.class);

            assertThat(result.items()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Round-Trip Tests")
    class RoundTripTests {

        @Test
        @DisplayName("Should serialize and deserialize ScoreResult")
        void shouldRoundTripScoreResult() throws JsonProcessingException {
            ScoreResult original = new ScoreResult(95, "Excellent match for criteria");

            String json = objectMapper.writeValueAsString(original);
            ScoreResult parsed = objectMapper.readValue(json, ScoreResult.class);

            assertThat(parsed).isEqualTo(original);
        }

        @Test
        @DisplayName("Should serialize and deserialize EnrichmentResult")
        void shouldRoundTripEnrichmentResult() throws JsonProcessingException {
            EntitiesResult entities = new EntitiesResult(
                    List.of("Elon Musk"),
                    List.of("Tesla", "SpaceX"),
                    List.of("Starship")
            );
            EnrichmentResult original = new EnrichmentResult(
                    "SpaceX launches new rocket",
                    List.of("Space", "Technology"),
                    entities,
                    "positive"
            );

            String json = objectMapper.writeValueAsString(original);
            EnrichmentResult parsed = objectMapper.readValue(json, EnrichmentResult.class);

            assertThat(parsed).isEqualTo(original);
        }

        @Test
        @DisplayName("Should serialize and deserialize ExtractedNewsItemList")
        void shouldRoundTripExtractedNewsItemList() throws JsonProcessingException {
            ExtractedNewsItemList original = new ExtractedNewsItemList(List.of(
                    new ExtractedNewsItem("Title 1", "Summary 1", "https://example.com/1"),
                    new ExtractedNewsItem("Title 2", "Summary 2", null)
            ));

            String json = objectMapper.writeValueAsString(original);
            ExtractedNewsItemList parsed = objectMapper.readValue(json, ExtractedNewsItemList.class);

            assertThat(parsed).isEqualTo(original);
            assertThat(parsed.items()).hasSize(2);
        }

        @Test
        @DisplayName("Should serialize and deserialize ExtractedWebItemList")
        void shouldRoundTripExtractedWebItemList() throws JsonProcessingException {
            ExtractedWebItemList original = new ExtractedWebItemList(List.of(
                    new ExtractedWebItem("Web Title 1", "Content 1", "/link1"),
                    new ExtractedWebItem("Web Title 2", "Content 2", "https://example.com/link2")
            ));

            String json = objectMapper.writeValueAsString(original);
            ExtractedWebItemList parsed = objectMapper.readValue(json, ExtractedWebItemList.class);

            assertThat(parsed).isEqualTo(original);
            assertThat(parsed.items()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Nullable Field Schema Generation Tests")
    class NullableFieldSchemaTests {

        /**
         * Test record with @Nullable field - mirrors ExtractedNewsItem
         */
        record TestItemWithNullable(
                String title,
                String summary,
                @Nullable String url
        ) {}

        /**
         * Test record without any @Nullable fields
         */
        record TestItemAllRequired(
                String title,
                String summary,
                String url
        ) {}

        /**
         * Wrapper for list - mirrors ExtractedNewsItemList
         */
        record TestItemList(List<TestItemWithNullable> items) {}

        @Test
        @DisplayName("Should detect @Nullable annotation on record component")
        void shouldDetectNullableAnnotation() {
            Set<String> nullableFields = collectNullableFields(TestItemWithNullable.class);

            assertThat(nullableFields).containsExactly("url");
            assertThat(nullableFields).doesNotContain("title", "summary");
        }

        @Test
        @DisplayName("Should return empty set when no @Nullable fields")
        void shouldReturnEmptySetForNoNullableFields() {
            Set<String> nullableFields = collectNullableFields(TestItemAllRequired.class);

            assertThat(nullableFields).isEmpty();
        }

        @Test
        @DisplayName("Should detect @Nullable in nested records via wrapper")
        void shouldDetectNullableInNestedRecords() {
            Set<String> nullableFields = collectNullableFields(TestItemList.class);

            assertThat(nullableFields).containsExactly("url");
        }

        @Test
        @DisplayName("Should generate schema with nullable type for @Nullable field")
        void shouldGenerateNullableTypeInSchema() throws JsonProcessingException {
            String schema = generateOpenAiSchema(TestItemWithNullable.class);
            JsonNode schemaNode = objectMapper.readTree(schema);

            JsonNode urlType = schemaNode.at("/properties/url/type");

            // Should be ["string", "null"] array, not just "string"
            assertThat(urlType.isArray()).isTrue();
            assertThat(urlType.get(0).asText()).isEqualTo("string");
            assertThat(urlType.get(1).asText()).isEqualTo("null");
        }

        @Test
        @DisplayName("Should generate schema with string type for non-nullable field")
        void shouldGenerateStringTypeForNonNullableField() throws JsonProcessingException {
            String schema = generateOpenAiSchema(TestItemWithNullable.class);
            JsonNode schemaNode = objectMapper.readTree(schema);

            JsonNode titleType = schemaNode.at("/properties/title/type");
            JsonNode summaryType = schemaNode.at("/properties/summary/type");

            // Should be plain "string", not array
            assertThat(titleType.isTextual()).isTrue();
            assertThat(titleType.asText()).isEqualTo("string");
            assertThat(summaryType.isTextual()).isTrue();
            assertThat(summaryType.asText()).isEqualTo("string");
        }

        @Test
        @DisplayName("Should include all fields in required array (OpenAI requirement)")
        void shouldIncludeAllFieldsInRequiredArray() throws JsonProcessingException {
            String schema = generateOpenAiSchema(TestItemWithNullable.class);
            JsonNode schemaNode = objectMapper.readTree(schema);

            JsonNode required = schemaNode.get("required");

            assertThat(required.isArray()).isTrue();
            List<String> requiredFields = new java.util.ArrayList<>();
            required.forEach(node -> requiredFields.add(node.asText()));
            assertThat(requiredFields).containsExactlyInAnyOrder("title", "summary", "url");
        }

        @Test
        @DisplayName("Should generate correct schema for ExtractedNewsItemList")
        void shouldGenerateCorrectSchemaForExtractedNewsItemList() throws JsonProcessingException {
            String schema = generateOpenAiSchema(ExtractedNewsItemList.class);
            JsonNode schemaNode = objectMapper.readTree(schema);

            // Navigate to the url field in the nested item schema
            JsonNode urlType = schemaNode.at("/properties/items/items/properties/url/type");

            // Should be ["string", "null"] array
            assertThat(urlType.isArray())
                    .as("url type should be array for nullable field, got: %s", urlType)
                    .isTrue();
            assertThat(urlType.get(0).asText()).isEqualTo("string");
            assertThat(urlType.get(1).asText()).isEqualTo("null");
        }

        @Test
        @DisplayName("Should set additionalProperties false on all objects")
        void shouldSetAdditionalPropertiesFalse() throws JsonProcessingException {
            String schema = generateOpenAiSchema(TestItemWithNullable.class);
            JsonNode schemaNode = objectMapper.readTree(schema);

            assertThat(schemaNode.get("additionalProperties").asBoolean()).isFalse();
        }

        // ========== Helper methods mirroring SpringAiService implementation ==========

        private String generateOpenAiSchema(Class<?> type) throws JsonProcessingException {
            JsonSchema schema = schemaGenerator.generateSchema(type);
            String schemaJson = objectMapper.writeValueAsString(schema);

            Set<String> nullableFields = collectNullableFields(type);

            JsonNode schemaNode = objectMapper.readTree(schemaJson);
            addAdditionalPropertiesFalse(schemaNode, nullableFields);

            return objectMapper.writeValueAsString(schemaNode);
        }

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

            if (type.isRecord()) {
                for (RecordComponent component : type.getRecordComponents()) {
                    if (component.isAnnotationPresent(Nullable.class)) {
                        nullableFields.add(component.getName());
                    }
                    collectNullableFieldsRecursive(component.getType(), nullableFields, visited);
                    if (component.getGenericType() instanceof java.lang.reflect.ParameterizedType pt) {
                        for (var arg : pt.getActualTypeArguments()) {
                            if (arg instanceof Class<?> argClass) {
                                collectNullableFieldsRecursive(argClass, nullableFields, visited);
                            }
                        }
                    }
                }
            }

            for (var field : type.getDeclaredFields()) {
                if (field.isAnnotationPresent(Nullable.class)) {
                    nullableFields.add(field.getName());
                }
                collectNullableFieldsRecursive(field.getType(), nullableFields, visited);
            }
        }

        private void addAdditionalPropertiesFalse(JsonNode node, Set<String> nullableFields) {
            if (node.isObject()) {
                ObjectNode objectNode = (ObjectNode) node;

                JsonNode typeNode = objectNode.get("type");
                JsonNode propertiesNode = objectNode.get("properties");
                if (typeNode != null && "object".equals(typeNode.asText())) {
                    objectNode.put("additionalProperties", false);

                    if (propertiesNode != null && propertiesNode.isObject()) {
                        var requiredArray = objectMapper.createArrayNode();
                        propertiesNode.fieldNames().forEachRemaining(fieldName -> {
                            requiredArray.add(fieldName);
                            if (nullableFields.contains(fieldName)) {
                                JsonNode propNode = propertiesNode.get(fieldName);
                                if (propNode.isObject()) {
                                    ObjectNode propObjectNode = (ObjectNode) propNode;
                                    JsonNode propTypeNode = propObjectNode.get("type");
                                    if (propTypeNode != null && propTypeNode.isTextual()) {
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

                node.fields().forEachRemaining(entry -> addAdditionalPropertiesFalse(entry.getValue(), nullableFields));
            } else if (node.isArray()) {
                node.forEach(child -> addAdditionalPropertiesFalse(child, nullableFields));
            }
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle null fields in entities")
        void shouldHandleNullFieldsInEntities() throws JsonProcessingException {
            String json = """
                {
                    "summary": "Brief article",
                    "topics": ["News"],
                    "entities": {
                        "people": null,
                        "companies": [],
                        "technologies": null
                    },
                    "sentiment": "neutral"
                }
                """;

            EnrichmentResult result = objectMapper.readValue(json, EnrichmentResult.class);

            assertThat(result.summary()).isEqualTo("Brief article");
            assertThat(result.entities().people()).isNull();
            assertThat(result.entities().companies()).isEmpty();
            assertThat(result.entities().technologies()).isNull();
        }

        @Test
        @DisplayName("Should handle score at boundary values")
        void shouldHandleScoreBoundaryValues() throws JsonProcessingException {
            String jsonZero = """
                {"score": 0, "reasoning": "Not relevant"}
                """;
            String jsonMax = """
                {"score": 100, "reasoning": "Perfect match"}
                """;

            ScoreResult zeroResult = objectMapper.readValue(jsonZero, ScoreResult.class);
            ScoreResult maxResult = objectMapper.readValue(jsonMax, ScoreResult.class);

            assertThat(zeroResult.score()).isEqualTo(0);
            assertThat(maxResult.score()).isEqualTo(100);
        }

        @Test
        @DisplayName("Should handle empty topics list")
        void shouldHandleEmptyTopicsList() throws JsonProcessingException {
            String json = """
                {
                    "summary": "Generic article",
                    "topics": [],
                    "entities": {
                        "people": [],
                        "companies": [],
                        "technologies": []
                    },
                    "sentiment": "neutral"
                }
                """;

            EnrichmentResult result = objectMapper.readValue(json, EnrichmentResult.class);

            assertThat(result.topics()).isEmpty();
        }

        @Test
        @DisplayName("Should handle special characters in strings")
        void shouldHandleSpecialCharacters() throws JsonProcessingException {
            String json = """
                {
                    "subject": "Breaking: \\"AI\\" beats humans at chess! 🎉",
                    "summary": "In a surprising turn, the AI's performance exceeded all expectations."
                }
                """;

            ReportEmailContent result = objectMapper.readValue(json, ReportEmailContent.class);

            assertThat(result.subject()).contains("\"AI\"");
            assertThat(result.subject()).contains("🎉");
        }
    }
}
