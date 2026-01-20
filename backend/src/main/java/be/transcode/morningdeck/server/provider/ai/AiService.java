package be.transcode.morningdeck.server.provider.ai;

import be.transcode.morningdeck.server.provider.ai.model.EnrichmentResult;
import be.transcode.morningdeck.server.provider.ai.model.EnrichmentWithScoreResult;
import be.transcode.morningdeck.server.provider.ai.model.ExtractedNewsItem;
import be.transcode.morningdeck.server.provider.ai.model.ExtractedWebItem;
import be.transcode.morningdeck.server.provider.ai.model.ReportEmailContent;
import be.transcode.morningdeck.server.provider.ai.model.ScoreResult;

import java.util.List;

/**
 * Service interface for AI-powered news processing.
 */
public interface AiService {

    /**
     * Enrich a news item with summary and tags in a single call.
     *
     * @param title   The article title
     * @param content The article content (raw or clean)
     * @return Enrichment result with summary, topics, entities, and sentiment
     */
    EnrichmentResult enrich(String title, String content);

    /**
     * Score a news item against briefing criteria.
     *
     * @param title            The article title
     * @param summary          The article summary
     * @param briefingCriteria The user's briefing criteria/interests
     * @return Score result with 0-100 score and reasoning
     */
    ScoreResult score(String title, String summary, String briefingCriteria);

    /**
     * Enrich a news item and score it against briefing criteria in a single call.
     * This combines enrichment and scoring to reduce LLM calls.
     *
     * @param title            The article title
     * @param content          The article content (raw or clean)
     * @param briefingCriteria The user's briefing criteria/interests
     * @return Combined enrichment and score result
     */
    EnrichmentWithScoreResult enrichWithScore(String title, String content, String briefingCriteria);

    /**
     * Enrich a news item and score it against briefing criteria, with optional web content.
     * When web content is available, it provides the full article for better analysis.
     *
     * @param title            The article title
     * @param content          The original article content (from feed/email)
     * @param webContent       The fetched web content (nullable, may be empty)
     * @param briefingCriteria The user's briefing criteria/interests
     * @return Combined enrichment and score result
     */
    EnrichmentWithScoreResult enrichWithScore(String title, String content, String webContent, String briefingCriteria);

    /**
     * Extract news items from an email (newsletter).
     * Analyzes the email content and extracts up to 5 distinct news items.
     *
     * @param subject The email subject
     * @param content The email body content (HTML converted to markdown)
     * @return List of extracted news items (up to 5)
     */
    List<ExtractedNewsItem> extractFromEmail(String subject, String content);

    /**
     * Extract news items from a web page.
     * Analyzes the page content using the provided extraction prompt.
     *
     * @param pageContent      The web page content (HTML converted to markdown)
     * @param extractionPrompt User-defined prompt describing what to extract
     * @return List of extracted news items (up to 50)
     */
    List<ExtractedWebItem> extractFromWeb(String pageContent, String extractionPrompt);

    /**
     * Generate email content (subject and summary) for a daily report.
     *
     * @param briefingName        The name of the briefing
     * @param briefingDescription The description of the briefing (may be null)
     * @param items               Formatted list of news items with titles, summaries, and scores
     * @return ReportEmailContent with subject and summary
     */
    ReportEmailContent generateReportEmailContent(String briefingName, String briefingDescription, String items);
}
