package be.transcode.morningdeck.server.provider.ai.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/**
 * Named entities extracted from a news article.
 */
@JsonPropertyOrder({"people", "companies", "technologies"})
public record EntitiesResult(
        List<String> people,
        List<String> companies,
        List<String> technologies
) {
}
