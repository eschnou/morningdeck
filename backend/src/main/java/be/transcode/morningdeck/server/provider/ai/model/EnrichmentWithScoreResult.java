package be.transcode.morningdeck.server.provider.ai.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/**
 * Combined result of enriching a news article with summary, tags, and relevance score.
 * Used for single-call enrichment + scoring during item processing.
 */
@JsonPropertyOrder({"summary", "topics", "entities", "sentiment", "score", "scoreReasoning"})
public record EnrichmentWithScoreResult(
        String summary,
        List<String> topics,
        EntitiesResult entities,
        String sentiment,
        Integer score,
        String scoreReasoning
) {
    /**
     * Create from separate enrichment and score results.
     */
    public static EnrichmentWithScoreResult from(EnrichmentResult enrichment, ScoreResult score) {
        return new EnrichmentWithScoreResult(
                enrichment.summary(),
                enrichment.topics(),
                enrichment.entities(),
                enrichment.sentiment(),
                score != null ? score.score() : null,
                score != null ? score.reasoning() : null
        );
    }
}
