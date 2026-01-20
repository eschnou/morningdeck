package be.transcode.morningdeck.server.core.queue;

import be.transcode.morningdeck.server.core.model.DayBrief;
import be.transcode.morningdeck.server.core.model.NewsItem;
import be.transcode.morningdeck.server.core.model.NewsItemStatus;
import be.transcode.morningdeck.server.core.model.Source;
import be.transcode.morningdeck.server.core.repository.NewsItemRepository;
import be.transcode.morningdeck.server.core.service.SubscriptionService;
import be.transcode.morningdeck.server.provider.ai.AiService;
import be.transcode.morningdeck.server.provider.ai.model.EnrichmentWithScoreResult;
import be.transcode.morningdeck.server.provider.ai.model.EntitiesResult;
import be.transcode.morningdeck.server.provider.webfetch.WebContentFetcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@DisplayName("ProcessingWorker Unit Tests")
@ExtendWith(MockitoExtension.class)
class ProcessingWorkerTest {

    @Mock
    private NewsItemRepository newsItemRepository;

    @Mock
    private AiService aiService;

    @Mock
    private WebContentFetcher webContentFetcher;

    @Mock
    private SubscriptionService subscriptionService;

    private ProcessingWorker processingWorker;

    @BeforeEach
    void setUp() {
        processingWorker = new ProcessingWorker(newsItemRepository, aiService, webContentFetcher, subscriptionService);
        // Default: user has credits and deduction succeeds
        // Use lenient() because not all tests exercise the credit deduction path
        lenient().when(subscriptionService.useCredits(any(UUID.class), anyInt())).thenReturn(true);
    }

    private NewsItem createNewsItem(String link, String cleanContent) {
        DayBrief dayBrief = DayBrief.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .briefing("Test briefing criteria")
                .position(0)
                .build();

        Source source = Source.builder()
                .id(UUID.randomUUID())
                .dayBrief(dayBrief)
                .build();

        return NewsItem.builder()
                .id(UUID.randomUUID())
                .title("Test Article")
                .link(link)
                .cleanContent(cleanContent)
                .status(NewsItemStatus.PENDING)
                .source(source)
                .build();
    }

    private EnrichmentWithScoreResult createMockResult() {
        return new EnrichmentWithScoreResult(
                "Test summary",
                List.of("Technology"),
                new EntitiesResult(List.of(), List.of(), List.of()),
                "neutral",
                75,
                "Test reasoning"
        );
    }

    @Test
    @DisplayName("Should fetch web content when link is present and content is short")
    void shouldFetchWebContentWhenLinkPresent() {
        NewsItem item = createNewsItem("https://example.com/article", "Short content");
        when(newsItemRepository.findById(item.getId())).thenReturn(Optional.of(item));
        when(webContentFetcher.fetch("https://example.com/article")).thenReturn(Optional.of("Full web content"));
        when(aiService.enrichWithScore(anyString(), anyString(), anyString(), anyString())).thenReturn(createMockResult());

        processingWorker.process(item.getId());

        verify(webContentFetcher).fetch("https://example.com/article");
        assertThat(item.getWebContent()).isEqualTo("Full web content");
    }

    @Test
    @DisplayName("Should skip fetch when content exceeds 2000 chars")
    void shouldSkipFetchWhenContentOver2000Chars() {
        String longContent = "x".repeat(2500);
        NewsItem item = createNewsItem("https://example.com/article", longContent);
        when(newsItemRepository.findById(item.getId())).thenReturn(Optional.of(item));
        when(aiService.enrichWithScore(anyString(), anyString(), isNull(), anyString())).thenReturn(createMockResult());

        processingWorker.process(item.getId());

        verify(webContentFetcher, never()).fetch(anyString());
        assertThat(item.getWebContent()).isNull();
    }

    @Test
    @DisplayName("Should skip fetch for non-HTTP link")
    void shouldSkipFetchForNonHttpLink() {
        NewsItem item = createNewsItem("mailto:test@example.com", "Short content");
        when(newsItemRepository.findById(item.getId())).thenReturn(Optional.of(item));
        when(aiService.enrichWithScore(anyString(), anyString(), isNull(), anyString())).thenReturn(createMockResult());

        processingWorker.process(item.getId());

        verify(webContentFetcher, never()).fetch(anyString());
    }

    @Test
    @DisplayName("Should skip fetch for null link")
    void shouldSkipFetchForNullLink() {
        NewsItem item = createNewsItem(null, "Short content");
        when(newsItemRepository.findById(item.getId())).thenReturn(Optional.of(item));
        when(aiService.enrichWithScore(anyString(), anyString(), isNull(), anyString())).thenReturn(createMockResult());

        processingWorker.process(item.getId());

        verify(webContentFetcher, never()).fetch(anyString());
    }

    @Test
    @DisplayName("Should proceed when fetch fails")
    void shouldProceedWhenFetchFails() {
        NewsItem item = createNewsItem("https://example.com/article", "Short content");
        when(newsItemRepository.findById(item.getId())).thenReturn(Optional.of(item));
        when(webContentFetcher.fetch("https://example.com/article")).thenReturn(Optional.empty());
        when(aiService.enrichWithScore(anyString(), anyString(), isNull(), anyString())).thenReturn(createMockResult());

        processingWorker.process(item.getId());

        verify(aiService).enrichWithScore(eq("Test Article"), eq("Short content"), isNull(), eq("Test briefing criteria"));
        assertThat(item.getStatus()).isEqualTo(NewsItemStatus.DONE);
    }

    @Test
    @DisplayName("Should pass web content to AI service when fetched")
    void shouldPassWebContentToAiService() {
        NewsItem item = createNewsItem("https://example.com/article", "Short content");
        when(newsItemRepository.findById(item.getId())).thenReturn(Optional.of(item));
        when(webContentFetcher.fetch("https://example.com/article")).thenReturn(Optional.of("Full web content"));
        when(aiService.enrichWithScore(anyString(), anyString(), anyString(), anyString())).thenReturn(createMockResult());

        processingWorker.process(item.getId());

        ArgumentCaptor<String> webContentCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiService).enrichWithScore(eq("Test Article"), eq("Short content"), webContentCaptor.capture(), eq("Test briefing criteria"));
        assertThat(webContentCaptor.getValue()).isEqualTo("Full web content");
    }

    @Test
    @DisplayName("shouldFetchWebContent returns true for valid HTTP URL with short content")
    void shouldFetchWebContentReturnsTrueForValidUrl() {
        NewsItem item = createNewsItem("https://example.com/article", "Short");
        assertThat(processingWorker.shouldFetchWebContent(item)).isTrue();
    }

    @Test
    @DisplayName("shouldFetchWebContent returns false for mailto link")
    void shouldFetchWebContentReturnsFalseForMailto() {
        NewsItem item = createNewsItem("mailto:test@example.com", "Short");
        assertThat(processingWorker.shouldFetchWebContent(item)).isFalse();
    }

    @Test
    @DisplayName("shouldFetchWebContent returns false for long content")
    void shouldFetchWebContentReturnsFalseForLongContent() {
        NewsItem item = createNewsItem("https://example.com/article", "x".repeat(2500));
        assertThat(processingWorker.shouldFetchWebContent(item)).isFalse();
    }
}
