package be.transcode.morningdeck.server.provider.ai.model;

/**
 * Represents a news item extracted from a web page by AI.
 *
 * @param title   A concise title for the news item
 * @param content 2-3 sentence summary/description
 * @param link    The URL to the item (may be relative)
 */
public record ExtractedWebItem(
        String title,
        String content,
        String link
) {}
