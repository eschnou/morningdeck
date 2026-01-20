package be.transcode.morningdeck.server.provider.ai.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/**
 * Combined result of enriching a news article with summary and tags.
 */
@JsonPropertyOrder({"summary", "topics", "entities", "sentiment"})
public record EnrichmentResult(
        String summary,
        List<String> topics,
        EntitiesResult entities,
        String sentiment
) {
}
