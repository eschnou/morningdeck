package be.transcode.morningdeck.server.core.service;

import be.transcode.morningdeck.server.core.dto.NewsItemDTO;
import be.transcode.morningdeck.server.core.model.DayBrief;
import be.transcode.morningdeck.server.core.model.NewsItem;
import be.transcode.morningdeck.server.core.model.NewsItemStatus;
import be.transcode.morningdeck.server.core.model.Source;
import be.transcode.morningdeck.server.core.repository.NewsItemRepository;
import be.transcode.morningdeck.server.core.repository.SourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("NewsItemService Unit Tests")
@ExtendWith(MockitoExtension.class)
class NewsItemServiceTest {

    @Mock
    private NewsItemRepository newsItemRepository;

    @Mock
    private SourceRepository sourceRepository;

    private NewsItemService newsItemService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        newsItemService = new NewsItemService(newsItemRepository, sourceRepository);
        userId = UUID.randomUUID();
    }

    private NewsItem createNewsItem(String cleanContent, String webContent) {
        DayBrief dayBrief = DayBrief.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .position(0)
                .build();

        Source source = Source.builder()
                .id(UUID.randomUUID())
                .name("Test Source")
                .dayBrief(dayBrief)
                .build();

        return NewsItem.builder()
                .id(UUID.randomUUID())
                .title("Test Article")
                .link("https://example.com/article")
                .cleanContent(cleanContent)
                .webContent(webContent)
                .status(NewsItemStatus.DONE)
                .source(source)
                .build();
    }

    @Test
    @DisplayName("Should return webContent when available")
    void shouldReturnWebContentWhenAvailable() {
        NewsItem item = createNewsItem("Original clean content", "Full web content from fetch");
        when(newsItemRepository.findById(item.getId())).thenReturn(Optional.of(item));

        NewsItemDTO dto = newsItemService.getNewsItem(userId, item.getId());

        assertThat(dto.getContent()).isEqualTo("Full web content from fetch");
    }

    @Test
    @DisplayName("Should fallback to cleanContent when webContent is null")
    void shouldFallbackToCleanContentWhenWebContentNull() {
        NewsItem item = createNewsItem("Original clean content", null);
        when(newsItemRepository.findById(item.getId())).thenReturn(Optional.of(item));

        NewsItemDTO dto = newsItemService.getNewsItem(userId, item.getId());

        assertThat(dto.getContent()).isEqualTo("Original clean content");
    }

    @Test
    @DisplayName("Should fallback to cleanContent when webContent is blank")
    void shouldFallbackToCleanContentWhenWebContentBlank() {
        NewsItem item = createNewsItem("Original clean content", "   ");
        when(newsItemRepository.findById(item.getId())).thenReturn(Optional.of(item));

        NewsItemDTO dto = newsItemService.getNewsItem(userId, item.getId());

        assertThat(dto.getContent()).isEqualTo("Original clean content");
    }

    @Test
    @DisplayName("Should fallback to cleanContent when webContent is empty string")
    void shouldFallbackToCleanContentWhenWebContentEmpty() {
        NewsItem item = createNewsItem("Original clean content", "");
        when(newsItemRepository.findById(item.getId())).thenReturn(Optional.of(item));

        NewsItemDTO dto = newsItemService.getNewsItem(userId, item.getId());

        assertThat(dto.getContent()).isEqualTo("Original clean content");
    }
}
