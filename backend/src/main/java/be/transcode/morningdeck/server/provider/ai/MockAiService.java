package be.transcode.morningdeck.server.provider.ai;

import be.transcode.morningdeck.server.provider.ai.model.EnrichmentResult;
import be.transcode.morningdeck.server.provider.ai.model.EnrichmentWithScoreResult;
import be.transcode.morningdeck.server.provider.ai.model.EntitiesResult;
import be.transcode.morningdeck.server.provider.ai.model.ExtractedNewsItem;
import be.transcode.morningdeck.server.provider.ai.model.ExtractedWebItem;
import be.transcode.morningdeck.server.provider.ai.model.ReportEmailContent;
import be.transcode.morningdeck.server.provider.ai.model.ScoreResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Mock AI service for testing purposes.
 * Returns deterministic results without making external API calls.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "application.ai", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockAiService implements AiService {

    @Override
    public EnrichmentResult enrich(String title, String content) {
        log.debug("Mock enriching article: {}", title);
        String summary = "This is a mock summary for: " + title + ". " +
                "The article discusses relevant topics. " +
                "It provides important information for readers.";
        return new EnrichmentResult(
                summary,
                List.of("Technology", "News"),
                new EntitiesResult(
                        List.of(),
                        List.of(),
                        List.of()
                ),
                "neutral"
        );
    }

    @Override
    public ScoreResult score(String title, String summary, String briefingCriteria) {
        log.debug("Mock scoring article: {}", title);
        // Generate a deterministic score based on title length
        int score = Math.min(100, Math.max(0, (title.hashCode() % 100 + 100) % 100));
        return new ScoreResult(score, "Mock relevance score based on content analysis.");
    }

    @Override
    public EnrichmentWithScoreResult enrichWithScore(String title, String content, String briefingCriteria) {
        return enrichWithScore(title, content, null, briefingCriteria);
    }

    @Override
    public EnrichmentWithScoreResult enrichWithScore(String title, String content, String webContent, String briefingCriteria) {
        log.debug("Mock enriching and scoring article: {} (webContent: {})", title, webContent != null ? "present" : "absent");
        String summary = "This is a mock summary for: " + title + ". " +
                "The article discusses relevant topics. " +
                "It provides important information for readers.";
        // Generate a deterministic score based on title
        int score = Math.min(100, Math.max(0, (title.hashCode() % 100 + 100) % 100));
        return new EnrichmentWithScoreResult(
                summary,
                List.of("Technology", "News"),
                new EntitiesResult(
                        List.of(),
                        List.of(),
                        List.of()
                ),
                "neutral",
                score,
                "Mock relevance score based on content analysis against briefing criteria."
        );
    }

    @Override
    public List<ExtractedNewsItem> extractFromEmail(String subject, String content) {
        log.debug("Mock extracting news from email: {}", subject);
        return List.of(
                new ExtractedNewsItem(
                        "Mock: " + subject,
                        "This is a mock extraction from the email about " + subject + ". The newsletter contains relevant information for your briefing.",
                        null
                )
        );
    }

    @Override
    public List<ExtractedWebItem> extractFromWeb(String pageContent, String extractionPrompt) {
        log.debug("Mock extracting news from web page with prompt: {}", extractionPrompt);
        return List.of(
                new ExtractedWebItem(
                        "Mock Web Item 1",
                        "This is a mock extraction from the web page. The content matches the extraction criteria.",
                        "https://example.com/article/1"
                ),
                new ExtractedWebItem(
                        "Mock Web Item 2",
                        "Another mock item extracted from the web page based on the provided prompt.",
                        "/article/2"
                )
        );
    }

    @Override
    public ReportEmailContent generateReportEmailContent(String briefingName, String briefingDescription, String items) {
        log.debug("Mock generating email content for briefing: {}", briefingName);
        return new ReportEmailContent(
                "Key developments in today's news",
                "Today's briefing highlights several important developments. The top stories cover significant industry news and emerging trends. This mock summary provides an overview of the key points from your configured sources."
        );
    }
}
