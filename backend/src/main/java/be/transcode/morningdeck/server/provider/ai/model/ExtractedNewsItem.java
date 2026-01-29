package be.transcode.morningdeck.server.provider.ai.model;

import org.springframework.lang.Nullable;

/**
 * Represents a news item extracted from an email by AI.
 *
 * @param title   A concise title for the news item
 * @param summary 2-3 sentence summary of the key points
 * @param url     The original article URL if present in the email (nullable)
 */
public record ExtractedNewsItem(
        String title,
        String summary,
        @Nullable String url
) {}
