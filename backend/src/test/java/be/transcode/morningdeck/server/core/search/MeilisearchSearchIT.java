package be.transcode.morningdeck.server.core.search;

import be.transcode.morningdeck.server.config.TestConfig;
import be.transcode.morningdeck.server.config.TestcontainersConfiguration;
import be.transcode.morningdeck.server.core.dto.NewsItemDTO;
import be.transcode.morningdeck.server.core.model.*;
import static be.transcode.morningdeck.server.core.model.Role.USER;
import be.transcode.morningdeck.server.core.repository.DayBriefRepository;
import be.transcode.morningdeck.server.core.repository.NewsItemRepository;
import be.transcode.morningdeck.server.core.repository.SourceRepository;
import be.transcode.morningdeck.server.core.repository.UserRepository;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.model.TaskInfo;
import com.meilisearch.sdk.model.TaskStatus;
import io.vanslog.testcontainers.meilisearch.MeilisearchContainer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for Meilisearch search functionality.
 * Uses Testcontainers to spin up a real Meilisearch instance.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(TestConfig.class)
@ContextConfiguration(initializers = TestcontainersConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MeilisearchSearchIT {

    @Container
    static MeilisearchContainer meilisearch = new MeilisearchContainer(
            DockerImageName.parse("getmeili/meilisearch:v1.12"))
            .withMasterKey("testMasterKey123");

    @DynamicPropertySource
    static void configureMeilisearch(DynamicPropertyRegistry registry) {
        registry.add("meilisearch.enabled", () -> true);
        registry.add("meilisearch.host", () ->
                "http://" + meilisearch.getHost() + ":" + meilisearch.getMappedPort(7700));
        registry.add("meilisearch.api-key", () -> "testMasterKey123");
    }

    @Autowired
    private MeilisearchSearchService searchService;

    @Autowired
    private MeilisearchSyncService syncService;

    @Autowired
    private Client meilisearchClient;

    @Autowired
    private Index newsItemsIndex;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DayBriefRepository dayBriefRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private NewsItemRepository newsItemRepository;

    private User testUser;
    private User otherUser;
    private DayBrief testBrief;
    private DayBrief otherBrief;
    private Source testSource;

    @BeforeEach
    void setUp() throws Exception {
        // Delete all documents from the index before each test
        try {
            TaskInfo deleteTask = newsItemsIndex.deleteAllDocuments();
            meilisearchClient.waitForTask(deleteTask.getTaskUid());
        } catch (Exception e) {
            // Ignore if index is empty
        }

        // Wait for index to be ready
        waitForIndexing();

        // Create test user
        testUser = userRepository.save(User.builder()
                .username("testuser_" + UUID.randomUUID().toString().substring(0, 8))
                .email("test_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com")
                .password("password")
                .name("Test User")
                .emailVerified(true)
                .role(USER)
                .build());

        // Create another user for isolation tests
        otherUser = userRepository.save(User.builder()
                .username("otheruser_" + UUID.randomUUID().toString().substring(0, 8))
                .email("other_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com")
                .password("password")
                .name("Other User")
                .emailVerified(true)
                .role(USER)
                .build());

        // Create test brief
        testBrief = dayBriefRepository.save(DayBrief.builder()
                .userId(testUser.getId())
                .title("Test Brief")
                .description("Test description")
                .briefing("Technology news")
                .frequency(BriefingFrequency.DAILY)
                .scheduleTime(LocalTime.of(8, 0))
                .timezone("UTC")
                .status(DayBriefStatus.ACTIVE)
                .position(0)
                .build());

        // Create brief for other user
        otherBrief = dayBriefRepository.save(DayBrief.builder()
                .userId(otherUser.getId())
                .title("Other Brief")
                .description("Other description")
                .briefing("Sports news")
                .frequency(BriefingFrequency.DAILY)
                .scheduleTime(LocalTime.of(8, 0))
                .timezone("UTC")
                .status(DayBriefStatus.ACTIVE)
                .position(0)
                .build());

        // Create test source
        testSource = sourceRepository.save(Source.builder()
                .dayBrief(testBrief)
                .name("TechCrunch")
                .url("https://techcrunch.com/feed")
                .type(SourceType.RSS)
                .status(SourceStatus.ACTIVE)
                .build());
    }

    @Test
    void shouldSearchArticlesByTitle() throws Exception {
        // Create and index a test article
        NewsItem item = createAndIndexNewsItem(
                "Apple announces new AI features",
                "Apple revealed new artificial intelligence capabilities...",
                testSource
        );

        // Wait for indexing
        waitForIndexing();

        // Search for the article
        ArticleSearchRequest request = ArticleSearchRequest.builder()
                .query("Apple AI")
                .userId(testUser.getId())
                .briefId(testBrief.getId())
                .build();

        Page<NewsItemDTO> results = searchService.search(request);

        assertThat(results.getTotalElements()).isGreaterThanOrEqualTo(1);
        assertThat(results.getContent())
                .extracting(NewsItemDTO::getTitle)
                .anyMatch(title -> title.contains("Apple"));
    }

    @Test
    void shouldNotLeakDataBetweenUsers() throws Exception {
        // Create article for test user
        NewsItem userArticle = createAndIndexNewsItem(
                "Secret article for test user",
                "This should only be visible to test user",
                testSource
        );

        // Create source and article for other user
        Source otherSource = sourceRepository.save(Source.builder()
                .dayBrief(otherBrief)
                .name("Other Source")
                .url("https://other.com/feed")
                .type(SourceType.RSS)
                .status(SourceStatus.ACTIVE)
                .build());

        NewsItem otherArticle = createAndIndexNewsItem(
                "Secret article for other user",
                "This should only be visible to other user",
                otherSource
        );

        waitForIndexing();

        // Search as test user - should find test user's article
        ArticleSearchRequest testUserRequest = ArticleSearchRequest.builder()
                .query("Secret article")
                .userId(testUser.getId())
                .briefId(testBrief.getId())
                .build();

        Page<NewsItemDTO> testUserResults = searchService.search(testUserRequest);
        assertThat(testUserResults.getContent())
                .extracting(NewsItemDTO::getId)
                .contains(userArticle.getId())
                .doesNotContain(otherArticle.getId());

        // Search as other user - should find other user's article
        ArticleSearchRequest otherUserRequest = ArticleSearchRequest.builder()
                .query("Secret article")
                .userId(otherUser.getId())
                .briefId(otherBrief.getId())
                .build();

        Page<NewsItemDTO> otherUserResults = searchService.search(otherUserRequest);
        assertThat(otherUserResults.getContent())
                .extracting(NewsItemDTO::getId)
                .contains(otherArticle.getId())
                .doesNotContain(userArticle.getId());
    }

    @Test
    void shouldRespectReadStatusFilter() throws Exception {
        // Create read and unread articles
        NewsItem readItem = createAndIndexNewsItem(
                "Read article about technology",
                "This article has been read",
                testSource
        );
        readItem.setReadAt(Instant.now());
        newsItemRepository.save(readItem);
        updateDocumentSync(readItem);

        NewsItem unreadItem = createAndIndexNewsItem(
                "Unread article about technology",
                "This article has not been read",
                testSource
        );

        waitForIndexing();

        // Search for unread only
        ArticleSearchRequest unreadRequest = ArticleSearchRequest.builder()
                .query("technology")
                .userId(testUser.getId())
                .briefId(testBrief.getId())
                .readStatus("UNREAD")
                .build();

        Page<NewsItemDTO> unreadResults = searchService.search(unreadRequest);
        assertThat(unreadResults.getContent())
                .extracting(NewsItemDTO::getId)
                .contains(unreadItem.getId())
                .doesNotContain(readItem.getId());
    }

    @Test
    void shouldRespectMinScoreFilter() throws Exception {
        // Create articles with different scores
        NewsItem highScoreItem = createAndIndexNewsItem(
                "Highly relevant article",
                "Very important news",
                testSource
        );
        highScoreItem.setScore(90);
        newsItemRepository.save(highScoreItem);
        updateDocumentSync(highScoreItem);

        NewsItem lowScoreItem = createAndIndexNewsItem(
                "Less relevant article",
                "Not so important news",
                testSource
        );
        lowScoreItem.setScore(30);
        newsItemRepository.save(lowScoreItem);
        updateDocumentSync(lowScoreItem);

        waitForIndexing();

        // Search with min score filter
        ArticleSearchRequest request = ArticleSearchRequest.builder()
                .query("article")
                .userId(testUser.getId())
                .briefId(testBrief.getId())
                .minScore(50)
                .build();

        Page<NewsItemDTO> results = searchService.search(request);
        assertThat(results.getContent())
                .extracting(NewsItemDTO::getId)
                .contains(highScoreItem.getId())
                .doesNotContain(lowScoreItem.getId());
    }

    @Test
    void shouldHandleTypoTolerance() throws Exception {
        // Create article
        createAndIndexNewsItem(
                "Technology revolution in artificial intelligence",
                "AI is changing everything",
                testSource
        );

        waitForIndexing();

        // Search with typo
        ArticleSearchRequest request = ArticleSearchRequest.builder()
                .query("tecnology")  // typo: should find "technology"
                .userId(testUser.getId())
                .briefId(testBrief.getId())
                .build();

        Page<NewsItemDTO> results = searchService.search(request);
        assertThat(results.getTotalElements()).isGreaterThanOrEqualTo(1);
    }

    private NewsItem createAndIndexNewsItem(String title, String content, Source source) {
        NewsItem item = NewsItem.builder()
                .source(source)
                .guid(UUID.randomUUID().toString())
                .title(title)
                .link("https://example.com/" + UUID.randomUUID())
                .cleanContent(content)
                .publishedAt(Instant.now())
                .status(NewsItemStatus.DONE)
                .build();

        item = newsItemRepository.save(item);
        // Index synchronously for testing
        indexDocumentSync(item);
        return item;
    }

    /**
     * Index or update a document synchronously for testing purposes.
     * Uses addDocuments which handles both insert and update in Meilisearch.
     */
    private void indexDocumentSync(NewsItem item) {
        syncDocumentSync(item, "index");
    }

    /**
     * Update a document synchronously for testing purposes.
     */
    private void updateDocumentSync(NewsItem item) {
        syncDocumentSync(item, "update");
    }

    private void syncDocumentSync(NewsItem item, String operation) {
        try {
            NewsItemSearchDocument doc = NewsItemSearchDocument.from(item);
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String json = mapper.writeValueAsString(List.of(doc));
            TaskInfo taskInfo = newsItemsIndex.addDocuments(json, "id");
            meilisearchClient.waitForTask(taskInfo.getTaskUid());
        } catch (Exception e) {
            throw new RuntimeException("Failed to " + operation + " document", e);
        }
    }

    private void waitForIndexing() {
        // Wait for all pending tasks to complete
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> {
                    var stats = newsItemsIndex.getStats();
                    return !stats.isIndexing();
                });
    }
}
