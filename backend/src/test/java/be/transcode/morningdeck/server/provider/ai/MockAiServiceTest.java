package be.transcode.morningdeck.server.provider.ai;

import be.transcode.morningdeck.server.provider.ai.model.EnrichmentResult;
import be.transcode.morningdeck.server.provider.ai.model.EnrichmentWithScoreResult;
import be.transcode.morningdeck.server.provider.ai.model.ExtractedNewsItem;
import be.transcode.morningdeck.server.provider.ai.model.ScoreResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MockAiService Unit Tests")
class MockAiServiceTest {

    private MockAiService mockAiService;

    @BeforeEach
    void setUp() {
        mockAiService = new MockAiService();
    }

    @Nested
    @DisplayName("Enrich Tests")
    class EnrichTests {

        @Test
        @DisplayName("Should return mock enrichment result")
        void shouldReturnMockEnrichmentResult() {
            EnrichmentResult result = mockAiService.enrich("Test Title", "Test content");

            assertThat(result).isNotNull();
            assertThat(result.summary()).contains("Test Title");
            assertThat(result.topics()).containsExactly("Technology", "News");
            assertThat(result.sentiment()).isEqualTo("neutral");
        }
    }

    @Nested
    @DisplayName("Score Tests")
    class ScoreTests {

        @Test
        @DisplayName("Should return deterministic score based on title")
        void shouldReturnDeterministicScore() {
            ScoreResult result1 = mockAiService.score("Test Title", "Summary", "Criteria");
            ScoreResult result2 = mockAiService.score("Test Title", "Summary", "Criteria");

            assertThat(result1.score()).isEqualTo(result2.score());
            assertThat(result1.score()).isBetween(0, 100);
            assertThat(result1.reasoning()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("Enrich With Score Tests")
    class EnrichWithScoreTests {

        @Test
        @DisplayName("Should return combined enrichment and score")
        void shouldReturnCombinedResult() {
            EnrichmentWithScoreResult result = mockAiService.enrichWithScore(
                    "Test Title", "Test content", "Briefing criteria"
            );

            assertThat(result).isNotNull();
            assertThat(result.summary()).contains("Test Title");
            assertThat(result.topics()).containsExactly("Technology", "News");
            assertThat(result.score()).isBetween(0, 100);
            assertThat(result.scoreReasoning()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("Extract From Email Tests")
    class ExtractFromEmailTests {

        @Test
        @DisplayName("Should extract single item from email")
        void shouldExtractSingleItemFromEmail() {
            List<ExtractedNewsItem> items = mockAiService.extractFromEmail(
                    "Newsletter Subject",
                    "Newsletter content with news items"
            );

            assertThat(items).hasSize(1);
            ExtractedNewsItem item = items.get(0);
            assertThat(item.title()).contains("Newsletter Subject");
            assertThat(item.summary()).isNotBlank();
            assertThat(item.url()).isNull();
        }

        @Test
        @DisplayName("Should include subject in extracted title")
        void shouldIncludeSubjectInExtractedTitle() {
            String subject = "Tech Weekly Digest";
            List<ExtractedNewsItem> items = mockAiService.extractFromEmail(subject, "Content");

            assertThat(items.get(0).title()).contains(subject);
        }

        @Test
        @DisplayName("Should return non-empty list for any input")
        void shouldReturnNonEmptyListForAnyInput() {
            List<ExtractedNewsItem> items = mockAiService.extractFromEmail("", "");

            assertThat(items).isNotEmpty();
        }
    }
}
