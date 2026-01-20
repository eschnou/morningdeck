package be.transcode.morningdeck.server.provider.ai.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Relevance score for a news article against briefing criteria.
 */
@JsonPropertyOrder({"score", "reasoning"})
public record ScoreResult(
        int score,
        String reasoning
) {
}
