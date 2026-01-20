package be.transcode.morningdeck.server.provider.ai;

import be.transcode.morningdeck.server.core.service.ApiUsageLogService;
import be.transcode.morningdeck.server.core.service.SubscriptionService;
import be.transcode.morningdeck.server.provider.ai.model.EnrichmentResult;
import be.transcode.morningdeck.server.provider.ai.model.EnrichmentWithScoreResult;
import be.transcode.morningdeck.server.provider.ai.model.EntitiesResult;
import be.transcode.morningdeck.server.provider.ai.model.ExtractedNewsItem;
import be.transcode.morningdeck.server.provider.ai.model.ExtractedWebItem;
import be.transcode.morningdeck.server.provider.ai.model.ReportEmailContent;
import be.transcode.morningdeck.server.provider.ai.model.ScoreResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.metadata.Usage;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("TrackedAiService Unit Tests")
@ExtendWith(MockitoExtension.class)
class TrackedAiServiceTest {

    @Mock
    private SpringAiService springAiService;

    @Mock
    private ApiUsageLogService usageLogService;

    @Mock
    private Usage mockUsage;

    @Mock
    private SubscriptionService subscriptionService;

    private TrackedAiService trackedAiService;

    @BeforeEach
    void setUp() {
        trackedAiService = new TrackedAiService(springAiService, usageLogService, subscriptionService);
        doNothing().when(usageLogService).logAsync(any(), any(), any(), any(), anyBoolean(), any(), anyLong());
        // Default: user has credits (safety net should pass)
        // Use lenient() because not all tests set user context, so this stub may be unused
        lenient().when(subscriptionService.getCreditsBalance(any())).thenReturn(100);
    }

    @AfterEach
    void tearDown() {
        AiUsageContext.clear();
    }

    private EntitiesResult emptyEntities() {
        return new EntitiesResult(List.of(), List.of(), List.of());
    }

    @Nested
    @DisplayName("Enrich Tests")
    class EnrichTests {

        @Test
        @DisplayName("Should delegate to SpringAiService and log usage")
        void shouldDelegateAndLogUsage() {
            UUID userId = UUID.randomUUID();
            AiUsageContext.setUserId(userId);

            EnrichmentResult expectedResult = new EnrichmentResult("summary", List.of("topic"), emptyEntities(), "positive");
            when(springAiService.enrichTracked("title", "content"))
                    .thenReturn(new AiCallResult<>(expectedResult, mockUsage, "gpt-4"));

            EnrichmentResult result = trackedAiService.enrich("title", "content");

            assertThat(result).isEqualTo(expectedResult);
            verify(usageLogService).logAsync(
                    eq(userId),
                    eq(AiFeature.ENRICH),
                    eq("gpt-4"),
                    eq(mockUsage),
                    eq(true),
                    isNull(),
                    anyLong()
            );
        }

        @Test
        @DisplayName("Should capture timing")
        void shouldCaptureTiming() {
            EnrichmentResult expectedResult = new EnrichmentResult("summary", List.of(), emptyEntities(), "neutral");
            when(springAiService.enrichTracked(anyString(), anyString()))
                    .thenReturn(new AiCallResult<>(expectedResult, null, null));

            trackedAiService.enrich("title", "content");

            ArgumentCaptor<Long> durationCaptor = ArgumentCaptor.forClass(Long.class);
            verify(usageLogService).logAsync(any(), any(), any(), any(), anyBoolean(), any(), durationCaptor.capture());
            assertThat(durationCaptor.getValue()).isGreaterThanOrEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Score Tests")
    class ScoreTests {

        @Test
        @DisplayName("Should delegate to SpringAiService with SCORE feature")
        void shouldDelegateWithScoreFeature() {
            ScoreResult expectedResult = new ScoreResult(85, "Highly relevant");
            when(springAiService.scoreTracked("title", "summary", "criteria"))
                    .thenReturn(new AiCallResult<>(expectedResult, mockUsage, "gpt-4"));

            ScoreResult result = trackedAiService.score("title", "summary", "criteria");

            assertThat(result).isEqualTo(expectedResult);
            verify(usageLogService).logAsync(any(), eq(AiFeature.SCORE), any(), any(), anyBoolean(), any(), anyLong());
        }
    }

    @Nested
    @DisplayName("EnrichWithScore Tests")
    class EnrichWithScoreTests {

        @Test
        @DisplayName("Should delegate to SpringAiService with ENRICH_SCORE feature")
        void shouldDelegateWithEnrichScoreFeature() {
            EnrichmentWithScoreResult expectedResult = new EnrichmentWithScoreResult(
                    "summary", List.of("topic"), emptyEntities(), "neutral", 75, "Good match"
            );
            when(springAiService.enrichWithScoreTracked("title", "content", null, "criteria"))
                    .thenReturn(new AiCallResult<>(expectedResult, mockUsage, "gpt-4"));

            EnrichmentWithScoreResult result = trackedAiService.enrichWithScore("title", "content", "criteria");

            assertThat(result).isEqualTo(expectedResult);
            verify(usageLogService).logAsync(any(), eq(AiFeature.ENRICH_SCORE), any(), any(), anyBoolean(), any(), anyLong());
        }

        @Test
        @DisplayName("Should pass webContent when provided")
        void shouldPassWebContent() {
            EnrichmentWithScoreResult expectedResult = new EnrichmentWithScoreResult(
                    "summary", List.of(), emptyEntities(), "positive", 90, "Excellent"
            );
            when(springAiService.enrichWithScoreTracked("title", "content", "webContent", "criteria"))
                    .thenReturn(new AiCallResult<>(expectedResult, mockUsage, "gpt-4"));

            trackedAiService.enrichWithScore("title", "content", "webContent", "criteria");

            verify(springAiService).enrichWithScoreTracked("title", "content", "webContent", "criteria");
        }
    }

    @Nested
    @DisplayName("ExtractFromEmail Tests")
    class ExtractFromEmailTests {

        @Test
        @DisplayName("Should delegate to SpringAiService with EMAIL_EXTRACT feature")
        void shouldDelegateWithEmailExtractFeature() {
            List<ExtractedNewsItem> expectedResult = List.of(
                    new ExtractedNewsItem("News Title", "Summary", "http://example.com")
            );
            when(springAiService.extractFromEmailTracked("subject", "content"))
                    .thenReturn(new AiCallResult<>(expectedResult, mockUsage, "gpt-4"));

            List<ExtractedNewsItem> result = trackedAiService.extractFromEmail("subject", "content");

            assertThat(result).isEqualTo(expectedResult);
            verify(usageLogService).logAsync(any(), eq(AiFeature.EMAIL_EXTRACT), any(), any(), anyBoolean(), any(), anyLong());
        }
    }

    @Nested
    @DisplayName("ExtractFromWeb Tests")
    class ExtractFromWebTests {

        @Test
        @DisplayName("Should delegate to SpringAiService with WEB_EXTRACT feature")
        void shouldDelegateWithWebExtractFeature() {
            List<ExtractedWebItem> expectedResult = List.of(
                    new ExtractedWebItem("Web Title", "Summary", "http://example.com")
            );
            when(springAiService.extractFromWebTracked("pageContent", "prompt"))
                    .thenReturn(new AiCallResult<>(expectedResult, mockUsage, "gpt-4"));

            List<ExtractedWebItem> result = trackedAiService.extractFromWeb("pageContent", "prompt");

            assertThat(result).isEqualTo(expectedResult);
            verify(usageLogService).logAsync(any(), eq(AiFeature.WEB_EXTRACT), any(), any(), anyBoolean(), any(), anyLong());
        }
    }

    @Nested
    @DisplayName("GenerateReportEmailContent Tests")
    class GenerateReportEmailContentTests {

        @Test
        @DisplayName("Should delegate to SpringAiService with REPORT_GEN feature")
        void shouldDelegateWithReportGenFeature() {
            ReportEmailContent expectedResult = new ReportEmailContent("Subject", "Summary");
            when(springAiService.generateReportEmailContentTracked("briefing", "desc", "items"))
                    .thenReturn(new AiCallResult<>(expectedResult, mockUsage, "gpt-4"));

            ReportEmailContent result = trackedAiService.generateReportEmailContent("briefing", "desc", "items");

            assertThat(result).isEqualTo(expectedResult);
            verify(usageLogService).logAsync(any(), eq(AiFeature.REPORT_GEN), any(), any(), anyBoolean(), any(), anyLong());
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should log error and rethrow exception")
        void shouldLogErrorAndRethrow() {
            RuntimeException exception = new RuntimeException("AI service failed");
            when(springAiService.enrichTracked(anyString(), anyString())).thenThrow(exception);

            assertThatThrownBy(() -> trackedAiService.enrich("title", "content"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("AI service failed");

            verify(usageLogService).logAsync(
                    any(),
                    eq(AiFeature.ENRICH),
                    isNull(),
                    isNull(),
                    eq(false),
                    eq("AI service failed"),
                    anyLong()
            );
        }
    }

    @Nested
    @DisplayName("User Context Tests")
    class UserContextTests {

        @Test
        @DisplayName("Should pass null userId when context is not set")
        void shouldPassNullWhenContextNotSet() {
            EnrichmentResult expectedResult = new EnrichmentResult("summary", List.of(), emptyEntities(), "neutral");
            when(springAiService.enrichTracked(anyString(), anyString()))
                    .thenReturn(new AiCallResult<>(expectedResult, null, null));

            trackedAiService.enrich("title", "content");

            verify(usageLogService).logAsync(
                    isNull(),
                    any(),
                    any(),
                    any(),
                    anyBoolean(),
                    any(),
                    anyLong()
            );
        }
    }
}
