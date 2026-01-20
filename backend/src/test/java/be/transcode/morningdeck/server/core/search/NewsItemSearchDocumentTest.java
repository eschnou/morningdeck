package be.transcode.morningdeck.server.core.search;

import be.transcode.morningdeck.server.core.model.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for NewsItemSearchDocument mapping.
 */
class NewsItemSearchDocumentTest {

    @Test
    void shouldMapAllFieldsCorrectly() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID briefId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Instant publishedAt = Instant.now();
        Instant createdAt = Instant.now().minusSeconds(60);
        Instant readAt = Instant.now().minusSeconds(30);

        DayBrief dayBrief = DayBrief.builder()
                .id(briefId)
                .userId(userId)
                .title("Test Brief")
                .position(0)
                .build();

        Source source = Source.builder()
                .id(sourceId)
                .dayBrief(dayBrief)
                .name("TechCrunch")
                .url("https://techcrunch.com/feed")
                .build();

        NewsItemTags tags = new NewsItemTags();
        tags.setTopics(List.of("technology", "AI"));
        tags.setPeople(List.of("Tim Cook"));
        tags.setCompanies(List.of("Apple", "OpenAI"));
        tags.setTechnologies(List.of("machine learning"));
        tags.setSentiment("positive");

        NewsItem item = NewsItem.builder()
                .id(itemId)
                .source(source)
                .title("Apple announces new AI features")
                .summary("Apple revealed new artificial intelligence capabilities...")
                .cleanContent("Full article content here...")
                .author("John Doe")
                .tags(tags)
                .score(85)
                .saved(true)
                .readAt(readAt)
                .publishedAt(publishedAt)
                .createdAt(createdAt)
                .build();

        // When
        NewsItemSearchDocument doc = NewsItemSearchDocument.from(item);

        // Then
        assertThat(doc.getId()).isEqualTo(itemId.toString());
        assertThat(doc.getTitle()).isEqualTo("Apple announces new AI features");
        assertThat(doc.getSummary()).isEqualTo("Apple revealed new artificial intelligence capabilities...");
        assertThat(doc.getContent()).isEqualTo("Full article content here...");
        assertThat(doc.getAuthor()).isEqualTo("John Doe");

        // Tags
        assertThat(doc.getTagsTopics()).containsExactly("technology", "AI");
        assertThat(doc.getTagsPeople()).containsExactly("Tim Cook");
        assertThat(doc.getTagsCompanies()).containsExactly("Apple", "OpenAI");
        assertThat(doc.getTagsTechnologies()).containsExactly("machine learning");
        assertThat(doc.getSentiment()).isEqualTo("positive");

        // Security metadata
        assertThat(doc.getUserId()).isEqualTo(userId.toString());
        assertThat(doc.getBriefId()).isEqualTo(briefId.toString());
        assertThat(doc.getSourceId()).isEqualTo(sourceId.toString());
        assertThat(doc.getSourceName()).isEqualTo("TechCrunch");

        // Filter fields
        assertThat(doc.isRead()).isTrue();
        assertThat(doc.isSaved()).isTrue();
        assertThat(doc.getScore()).isEqualTo(85);

        // Timestamps
        assertThat(doc.getPublishedAt()).isEqualTo(publishedAt.getEpochSecond());
        assertThat(doc.getCreatedAt()).isEqualTo(createdAt.getEpochSecond());
    }

    @Test
    void shouldHandleUnreadItem() {
        // Given - item with no readAt
        NewsItem item = createMinimalNewsItem();

        // When
        NewsItemSearchDocument doc = NewsItemSearchDocument.from(item);

        // Then
        assertThat(doc.isRead()).isFalse();
    }

    @Test
    void shouldHandleUnsavedItem() {
        // Given - item with saved = null (default)
        NewsItem item = createMinimalNewsItem();

        // When
        NewsItemSearchDocument doc = NewsItemSearchDocument.from(item);

        // Then
        assertThat(doc.isSaved()).isFalse();
    }

    @Test
    void shouldHandleNullTags() {
        // Given - item with no tags
        NewsItem item = createMinimalNewsItem();
        item.setTags(null);

        // When
        NewsItemSearchDocument doc = NewsItemSearchDocument.from(item);

        // Then
        assertThat(doc.getTagsTopics()).isEmpty();
        assertThat(doc.getTagsPeople()).isEmpty();
        assertThat(doc.getTagsCompanies()).isEmpty();
        assertThat(doc.getTagsTechnologies()).isEmpty();
        assertThat(doc.getSentiment()).isNull();
    }

    @Test
    void shouldPreferCleanContentOverWebContent() {
        // Given
        NewsItem item = createMinimalNewsItem();
        item.setCleanContent("Clean content");
        item.setWebContent("Web content");
        item.setRawContent("Raw content");

        // When
        NewsItemSearchDocument doc = NewsItemSearchDocument.from(item);

        // Then
        assertThat(doc.getContent()).isEqualTo("Clean content");
    }

    @Test
    void shouldFallbackToWebContentWhenCleanContentIsNull() {
        // Given
        NewsItem item = createMinimalNewsItem();
        item.setCleanContent(null);
        item.setWebContent("Web content");
        item.setRawContent("Raw content");

        // When
        NewsItemSearchDocument doc = NewsItemSearchDocument.from(item);

        // Then
        assertThat(doc.getContent()).isEqualTo("Web content");
    }

    @Test
    void shouldFallbackToRawContentWhenOthersAreNull() {
        // Given
        NewsItem item = createMinimalNewsItem();
        item.setCleanContent(null);
        item.setWebContent(null);
        item.setRawContent("Raw content");

        // When
        NewsItemSearchDocument doc = NewsItemSearchDocument.from(item);

        // Then
        assertThat(doc.getContent()).isEqualTo("Raw content");
    }

    @Test
    void shouldTruncateLongContent() {
        // Given
        NewsItem item = createMinimalNewsItem();
        String longContent = "x".repeat(15000);
        item.setCleanContent(longContent);

        // When
        NewsItemSearchDocument doc = NewsItemSearchDocument.from(item);

        // Then
        assertThat(doc.getContent()).hasSize(10000);
    }

    @Test
    void shouldThrowExceptionWhenSourceIsNull() {
        // Given
        NewsItem item = NewsItem.builder()
                .id(UUID.randomUUID())
                .title("Test")
                .source(null)
                .build();

        // When/Then
        assertThatThrownBy(() -> NewsItemSearchDocument.from(item))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source");
    }

    @Test
    void shouldThrowExceptionWhenDayBriefIsNull() {
        // Given
        Source source = Source.builder()
                .id(UUID.randomUUID())
                .dayBrief(null)
                .name("Test Source")
                .build();

        NewsItem item = NewsItem.builder()
                .id(UUID.randomUUID())
                .title("Test")
                .source(source)
                .build();

        // When/Then
        assertThatThrownBy(() -> NewsItemSearchDocument.from(item))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dayBrief");
    }

    @Test
    void shouldReturnNullForNullItem() {
        // When
        NewsItemSearchDocument doc = NewsItemSearchDocument.from(null);

        // Then
        assertThat(doc).isNull();
    }

    @Test
    void shouldHandleNullTimestamps() {
        // Given
        NewsItem item = createMinimalNewsItem();
        item.setPublishedAt(null);
        item.setCreatedAt(null);

        // When
        NewsItemSearchDocument doc = NewsItemSearchDocument.from(item);

        // Then
        assertThat(doc.getPublishedAt()).isNull();
        assertThat(doc.getCreatedAt()).isNull();
    }

    private NewsItem createMinimalNewsItem() {
        DayBrief dayBrief = DayBrief.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .title("Test Brief")
                .position(0)
                .build();

        Source source = Source.builder()
                .id(UUID.randomUUID())
                .dayBrief(dayBrief)
                .name("Test Source")
                .url("https://example.com/feed")
                .build();

        return NewsItem.builder()
                .id(UUID.randomUUID())
                .source(source)
                .title("Test Article")
                .guid(UUID.randomUUID().toString())
                .build();
    }
}
