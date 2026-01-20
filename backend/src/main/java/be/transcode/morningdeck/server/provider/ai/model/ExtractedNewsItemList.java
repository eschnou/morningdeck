package be.transcode.morningdeck.server.provider.ai.model;

import java.util.List;

/**
 * Wrapper for a list of extracted news items.
 * Used for JSON schema generation in structured AI responses.
 */
public record ExtractedNewsItemList(List<ExtractedNewsItem> items) {}
