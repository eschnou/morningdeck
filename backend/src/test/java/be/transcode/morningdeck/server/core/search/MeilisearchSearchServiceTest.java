package be.transcode.morningdeck.server.core.search;

import be.transcode.morningdeck.server.core.model.*;
import be.transcode.morningdeck.server.core.repository.NewsItemRepository;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.model.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MeilisearchSearchService.
 * Tests filter building logic and error handling.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MeilisearchSearchServiceTest {

    @Mock
    private Index newsItemsIndex;

    @Mock
    private NewsItemRepository newsItemRepository;

    private MeilisearchSearchService searchService;

    private UUID userId;
    private UUID briefId;
    private UUID sourceId;

    @BeforeEach
    void setUp() {
        searchService = new MeilisearchSearchService(newsItemsIndex, newsItemRepository);
        userId = UUID.randomUUID();
        briefId = UUID.randomUUID();
        sourceId = UUID.randomUUID();
    }

    @Test
    void shouldThrowExceptionWhenUserIdIsNull() {
        // Given
        ArticleSearchRequest request = ArticleSearchRequest.builder()
                .query("test")
                .userId(null)
                .briefId(briefId)
                .build();

        // When/Then
        assertThatThrownBy(() -> searchService.search(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId and briefId are required");
    }

    @Test
    void shouldThrowExceptionWhenBriefIdIsNull() {
        // Given
        ArticleSearchRequest request = ArticleSearchRequest.builder()
                .query("test")
                .userId(userId)
                .briefId(null)
                .build();

        // When/Then
        assertThatThrownBy(() -> searchService.search(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId and briefId are required");
    }

    @Test
    void shouldAlwaysIncludeUserIdAndBriefIdInFilter() throws Exception {
        // Given
        ArticleSearchRequest request = ArticleSearchRequest.builder()
                .query("test")
                .userId(userId)
                .briefId(briefId)
                .build();

        mockEmptySearchResult();

        // When
        searchService.search(request);

        // Then
        String filter = captureFilterString();

        // CRITICAL: user_id and brief_id must always be present
        assertThat(filter).contains("user_id = '" + userId + "'");
        assertThat(filter).contains("brief_id = '" + briefId + "'");
    }

    @Test
    void shouldIncludeSourceIdFilterWhenProvided() throws Exception {
        // Given
        ArticleSearchRequest request = ArticleSearchRequest.builder()
                .query("test")
                .userId(userId)
                .briefId(briefId)
                .sourceId(sourceId)
                .build();

        mockEmptySearchResult();

        // When
        searchService.search(request);

        // Then
        String filter = captureFilterString();
        assertThat(filter).contains("source_id = '" + sourceId + "'");
    }

    @Test
    void shouldIncludeReadStatusFilterForUnread() throws Exception {
        // Given
        ArticleSearchRequest request = ArticleSearchRequest.builder()
                .query("test")
                .userId(userId)
                .briefId(briefId)
                .readStatus("UNREAD")
                .build();

        mockEmptySearchResult();

        // When
        searchService.search(request);

        // Then
        String filter = captureFilterString();
        assertThat(filter).contains("is_read = false");
    }

    @Test
    void shouldIncludeReadStatusFilterForRead() throws Exception {
        // Given
        ArticleSearchRequest request = ArticleSearchRequest.builder()
                .query("test")
                .userId(userId)
                .briefId(briefId)
                .readStatus("READ")
                .build();

        mockEmptySearchResult();

        // When
        searchService.search(request);

        // Then
        String filter = captureFilterString();
        assertThat(filter).contains("is_read = true");
    }

    @Test
    void shouldIncludeSavedFilter() throws Exception {
        // Given
        ArticleSearchRequest request = ArticleSearchRequest.builder()
                .query("test")
                .userId(userId)
                .briefId(briefId)
                .saved(true)
                .build();

        mockEmptySearchResult();

        // When
        searchService.search(request);

        // Then
        String filter = captureFilterString();
        assertThat(filter).contains("saved = true");
    }

    @Test
    void shouldIncludeMinScoreFilter() throws Exception {
        // Given
        ArticleSearchRequest request = ArticleSearchRequest.builder()
                .query("test")
                .userId(userId)
                .briefId(briefId)
                .minScore(50)
                .build();

        mockEmptySearchResult();

        // When
        searchService.search(request);

        // Then
        String filter = captureFilterString();
        assertThat(filter).contains("score >= 50");
    }

    @Test
    void shouldCombineMultipleFiltersWithAnd() throws Exception {
        // Given
        ArticleSearchRequest request = ArticleSearchRequest.builder()
                .query("test")
                .userId(userId)
                .briefId(briefId)
                .sourceId(sourceId)
                .readStatus("UNREAD")
                .saved(true)
                .minScore(50)
                .build();

        mockEmptySearchResult();

        // When
        searchService.search(request);

        // Then
        String filter = captureFilterString();

        // Should contain all filters joined by AND
        assertThat(filter).contains("user_id = '" + userId + "'");
        assertThat(filter).contains("brief_id = '" + briefId + "'");
        assertThat(filter).contains("source_id = '" + sourceId + "'");
        assertThat(filter).contains("is_read = false");
        assertThat(filter).contains("saved = true");
        assertThat(filter).contains("score >= 50");
        assertThat(filter).contains(" AND ");
    }

    @Test
    void shouldUsePaginationFromRequest() throws Exception {
        // Given
        ArticleSearchRequest request = ArticleSearchRequest.builder()
                .query("test")
                .userId(userId)
                .briefId(briefId)
                .page(2)
                .size(25)
                .build();

        mockEmptySearchResult();

        // When
        searchService.search(request);

        // Then
        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(newsItemsIndex).search(captor.capture());

        SearchRequest meiliRequest = captor.getValue();
        assertThat(meiliRequest.getOffset()).isEqualTo(50); // page 2 * size 25
        assertThat(meiliRequest.getLimit()).isEqualTo(25);
    }

    @Test
    void shouldSortByPublishedAtDescByDefault() throws Exception {
        // Given
        ArticleSearchRequest request = ArticleSearchRequest.builder()
                .query("test")
                .userId(userId)
                .briefId(briefId)
                .build();

        mockEmptySearchResult();

        // When
        searchService.search(request);

        // Then
        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(newsItemsIndex).search(captor.capture());

        String[] sort = captor.getValue().getSort();
        assertThat(sort).contains("published_at:desc");
    }

    @Test
    void shouldReturnEmptyPageWhenNoResults() throws Exception {
        // Given
        ArticleSearchRequest request = ArticleSearchRequest.builder()
                .query("test")
                .userId(userId)
                .briefId(briefId)
                .build();

        mockEmptySearchResult();

        // When
        Page<?> result = searchService.search(request);

        // Then
        assertThat(result.isEmpty()).isTrue();
        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    @Test
    void shouldMapSearchResultsToNewsItemDTOs() throws Exception {
        // Given
        UUID itemId = UUID.randomUUID();
        ArticleSearchRequest request = ArticleSearchRequest.builder()
                .query("test")
                .userId(userId)
                .briefId(briefId)
                .build();

        // Create mock search result with hit
        HashMap<String, Object> hit = new HashMap<>();
        hit.put("id", itemId.toString());

        ArrayList<HashMap<String, Object>> hits = new ArrayList<>();
        hits.add(hit);

        SearchResult mockResult = mock(SearchResult.class);
        when(mockResult.getHits()).thenReturn(hits);
        when(mockResult.getEstimatedTotalHits()).thenReturn(1);
        when(newsItemsIndex.search(any(SearchRequest.class))).thenReturn(mockResult);

        // Create mock entity
        DayBrief dayBrief = DayBrief.builder()
                .id(briefId)
                .userId(userId)
                .position(0)
                .build();
        Source source = Source.builder()
                .id(sourceId)
                .dayBrief(dayBrief)
                .name("Test Source")
                .build();
        NewsItem item = NewsItem.builder()
                .id(itemId)
                .source(source)
                .title("Test Article")
                .summary("Test summary")
                .publishedAt(Instant.now())
                .build();

        when(newsItemRepository.findAllByIdWithSource(List.of(itemId))).thenReturn(List.of(item));

        // When
        Page<?> result = searchService.search(request);

        // Then
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void shouldWrapExceptionInSearchException() throws Exception {
        // Given
        ArticleSearchRequest request = ArticleSearchRequest.builder()
                .query("test")
                .userId(userId)
                .briefId(briefId)
                .build();

        when(newsItemsIndex.search(any(SearchRequest.class))).thenThrow(new RuntimeException("Connection failed"));

        // When/Then
        assertThatThrownBy(() -> searchService.search(request))
                .isInstanceOf(MeilisearchSearchService.SearchException.class)
                .hasMessageContaining("Search failed")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    // Helper methods

    private void mockEmptySearchResult() throws Exception {
        SearchResult mockResult = mock(SearchResult.class);
        when(mockResult.getHits()).thenReturn(new ArrayList<>());
        when(mockResult.getEstimatedTotalHits()).thenReturn(0);
        when(newsItemsIndex.search(any(SearchRequest.class))).thenReturn(mockResult);
    }

    private String captureFilterString() throws Exception {
        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(newsItemsIndex).search(captor.capture());
        String[] filters = captor.getValue().getFilter();
        assertThat(filters).hasSize(1);
        return filters[0];
    }
}
