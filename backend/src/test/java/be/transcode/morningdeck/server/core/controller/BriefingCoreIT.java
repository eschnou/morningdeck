package be.transcode.morningdeck.server.core.controller;

import be.transcode.morningdeck.server.config.TestConfig;
import be.transcode.morningdeck.server.config.TestcontainersConfiguration;
import be.transcode.morningdeck.server.core.dto.AuthResponse;
import be.transcode.morningdeck.server.core.dto.DayBriefDTO;
import be.transcode.morningdeck.server.core.dto.RegisterRequest;
import be.transcode.morningdeck.server.core.dto.SourceDTO;
import be.transcode.morningdeck.server.core.model.*;
import be.transcode.morningdeck.server.core.repository.DayBriefRepository;
import be.transcode.morningdeck.server.core.repository.NewsItemRepository;
import be.transcode.morningdeck.server.core.repository.SourceRepository;
import be.transcode.morningdeck.server.core.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the Briefing-Core refactoring.
 * Tests the new Source → DayBrief (many-to-one) relationship.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
@Import(TestConfig.class)
@ContextConfiguration(initializers = TestcontainersConfiguration.class)
class BriefingCoreIT {

    private static WireMockServer wireMockServer;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private DayBriefRepository dayBriefRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private NewsItemRepository newsItemRepository;

    private static final String BRIEFS_URL = "/daybriefs";
    private static final String SOURCES_URL = "/sources";
    private static final String AUTH_URL = "/auth";

    private String authToken;
    private User testUser;

    private static final String SAMPLE_RSS_FEED = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>Test Feed</title>
                <description>A test RSS feed</description>
                <link>http://example.com</link>
                <item>
                  <guid>test-item-1</guid>
                  <title>Test Article 1</title>
                  <link>http://example.com/article1</link>
                  <pubDate>Mon, 01 Jan 2024 00:00:00 GMT</pubDate>
                  <description>Test content for article 1</description>
                </item>
              </channel>
            </rss>
            """;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @BeforeEach
    void setUp() throws Exception {
        wireMockServer.resetAll();
        newsItemRepository.deleteAll();
        sourceRepository.deleteAll();
        dayBriefRepository.deleteAll();
        userRepository.deleteAll();

        // Register a test user
        RegisterRequest registerRequest = RegisterRequest.builder()
                .username("testuser")
                .name("Test User")
                .email("test@example.com")
                .password("password123")
                .build();

        MvcResult result = mockMvc.perform(post(AUTH_URL + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                AuthResponse.class
        );

        authToken = authResponse.getToken();
        testUser = userRepository.findByUsername(registerRequest.getUsername()).orElseThrow();

        // Setup WireMock to return RSS feed
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/feed.xml"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/rss+xml")
                        .withBody(SAMPLE_RSS_FEED)));
    }

    @Nested
    @DisplayName("DayBrief CRUD without sourceIds")
    class DayBriefCrudTests {

        @Test
        @DisplayName("Should create DayBrief without sources")
        void shouldCreateDayBriefWithoutSources() throws Exception {
            DayBriefDTO request = DayBriefDTO.builder()
                    .title("Morning Tech Brief")
                    .description("Daily tech news summary")
                    .briefing("I'm interested in AI and machine learning")
                    .frequency(BriefingFrequency.DAILY)
                    .scheduleTime(LocalTime.of(8, 0))
                    .timezone("UTC")
                    .build();

            mockMvc.perform(post(BRIEFS_URL)
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.title").value("Morning Tech Brief"))
                    .andExpect(jsonPath("$.sourceCount").value(0));

            // Verify database
            List<DayBrief> briefs = dayBriefRepository.findAll();
            assertThat(briefs).hasSize(1);
            assertThat(briefs.get(0).getSources()).isEmpty();
        }

        @Test
        @DisplayName("Should list DayBriefs")
        void shouldListDayBriefs() throws Exception {
            createTestDayBrief("Brief 1");
            createTestDayBrief("Brief 2");

            mockMvc.perform(get(BRIEFS_URL)
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2));
        }

        @Test
        @DisplayName("Should create WEEKLY DayBrief with scheduleDayOfWeek")
        void shouldCreateWeeklyDayBriefWithScheduleDayOfWeek() throws Exception {
            DayBriefDTO request = DayBriefDTO.builder()
                    .title("Weekly Tech Brief")
                    .description("Weekly tech news summary")
                    .briefing("I'm interested in AI and machine learning")
                    .frequency(BriefingFrequency.WEEKLY)
                    .scheduleDayOfWeek(DayOfWeek.FRIDAY)
                    .scheduleTime(LocalTime.of(9, 0))
                    .timezone("UTC")
                    .build();

            mockMvc.perform(post(BRIEFS_URL)
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.title").value("Weekly Tech Brief"))
                    .andExpect(jsonPath("$.frequency").value("WEEKLY"))
                    .andExpect(jsonPath("$.scheduleDayOfWeek").value("FRIDAY"));

            // Verify database
            List<DayBrief> briefs = dayBriefRepository.findAll();
            assertThat(briefs).hasSize(1);
            assertThat(briefs.get(0).getScheduleDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
        }

        @Test
        @DisplayName("Should update DayBrief scheduleDayOfWeek")
        void shouldUpdateDayBriefScheduleDayOfWeek() throws Exception {
            // Create a WEEKLY brief with MONDAY
            DayBrief brief = DayBrief.builder()
                    .userId(testUser.getId())
                    .title("Weekly Brief")
                    .briefing("Test criteria")
                    .frequency(BriefingFrequency.WEEKLY)
                    .scheduleDayOfWeek(DayOfWeek.MONDAY)
                    .scheduleTime(LocalTime.of(8, 0))
                    .timezone("UTC")
                    .status(DayBriefStatus.ACTIVE)
                    .position(0)
                    .build();
            brief = dayBriefRepository.save(brief);

            // Update to WEDNESDAY
            DayBriefDTO updateRequest = DayBriefDTO.builder()
                    .scheduleDayOfWeek(DayOfWeek.WEDNESDAY)
                    .build();

            mockMvc.perform(put(BRIEFS_URL + "/" + brief.getId())
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.scheduleDayOfWeek").value("WEDNESDAY"));

            // Verify database
            DayBrief updated = dayBriefRepository.findById(brief.getId()).orElseThrow();
            assertThat(updated.getScheduleDayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);
        }
    }

    @Nested
    @DisplayName("Briefing-Scoped Source Management")
    class BriefingScopedSourceTests {

        @Test
        @DisplayName("Should create source via briefing endpoint")
        void shouldCreateSourceViaBriefingEndpoint() throws Exception {
            DayBrief brief = createTestDayBrief("Test Brief");
            String feedUrl = wireMockServer.baseUrl() + "/feed.xml";

            SourceDTO request = SourceDTO.builder()
                    .url(feedUrl)
                    .type(SourceType.RSS)
                    .build();

            mockMvc.perform(post(BRIEFS_URL + "/" + brief.getId() + "/sources")
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.briefingId").value(brief.getId().toString()))
                    .andExpect(jsonPath("$.briefingTitle").value("Test Brief"))
                    .andExpect(jsonPath("$.name").value("Test Feed"));

            // Verify database relationship
            List<Source> sources = sourceRepository.findByDayBriefId(brief.getId());
            assertThat(sources).hasSize(1);
            assertThat(sources.get(0).getDayBrief().getId()).isEqualTo(brief.getId());
        }

        @Test
        @DisplayName("Should list sources for briefing")
        void shouldListSourcesForBriefing() throws Exception {
            DayBrief brief = createTestDayBrief("Test Brief");
            createTestSource(brief, "Feed 1");
            createTestSource(brief, "Feed 2");

            // Also create a source in another briefing - should not appear
            DayBrief otherBrief = createTestDayBrief("Other Brief");
            createTestSource(otherBrief, "Other Feed");

            mockMvc.perform(get(BRIEFS_URL + "/" + brief.getId() + "/sources")
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2));
        }

        @Test
        @DisplayName("Should create source via global endpoint with briefingId")
        void shouldCreateSourceViaGlobalEndpointWithBriefingId() throws Exception {
            DayBrief brief = createTestDayBrief("Test Brief");
            String feedUrl = wireMockServer.baseUrl() + "/feed.xml";

            SourceDTO request = SourceDTO.builder()
                    .briefingId(brief.getId())
                    .url(feedUrl)
                    .type(SourceType.RSS)
                    .build();

            mockMvc.perform(post(SOURCES_URL)
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.briefingId").value(brief.getId().toString()));
        }

        @Test
        @DisplayName("Should fail to create source without briefingId via global endpoint")
        void shouldFailToCreateSourceWithoutBriefingId() throws Exception {
            String feedUrl = wireMockServer.baseUrl() + "/feed.xml";

            SourceDTO request = SourceDTO.builder()
                    .url(feedUrl)
                    .type(SourceType.RSS)
                    .build();

            mockMvc.perform(post(SOURCES_URL)
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should fail to create source for other user's briefing")
        void shouldFailToCreateSourceForOtherUsersBriefing() throws Exception {
            // Create another user and their briefing
            User otherUser = User.builder()
                    .username("otheruser")
                    .email("other@example.com")
                    .password("password123")
                    .name("Other User")
                    .build();
            otherUser = userRepository.save(otherUser);

            DayBrief otherBrief = DayBrief.builder()
                    .userId(otherUser.getId())
                    .title("Other Brief")
                    .briefing("Other criteria")
                    .frequency(BriefingFrequency.DAILY)
                    .scheduleTime(LocalTime.of(8, 0))
                    .timezone("UTC")
                    .status(DayBriefStatus.ACTIVE)
                    .position(0)
                    .build();
            otherBrief = dayBriefRepository.save(otherBrief);

            String feedUrl = wireMockServer.baseUrl() + "/feed.xml";
            SourceDTO request = SourceDTO.builder()
                    .url(feedUrl)
                    .type(SourceType.RSS)
                    .build();

            // Try to add source to other user's briefing
            mockMvc.perform(post(BRIEFS_URL + "/" + otherBrief.getId() + "/sources")
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Source Ownership via Briefing")
    class SourceOwnershipTests {

        @Test
        @DisplayName("Should get source by ID with briefing info")
        void shouldGetSourceWithBriefingInfo() throws Exception {
            DayBrief brief = createTestDayBrief("Test Brief");
            Source source = createTestSource(brief, "Test Feed");

            mockMvc.perform(get(SOURCES_URL + "/" + source.getId())
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.briefingId").value(brief.getId().toString()))
                    .andExpect(jsonPath("$.briefingTitle").value("Test Brief"));
        }

        @Test
        @DisplayName("Source.getUserId() should return briefing owner's ID")
        void sourceGetUserIdShouldReturnBriefingOwnerId() {
            DayBrief brief = createTestDayBrief("Test Brief");
            Source source = createTestSource(brief, "Test Feed");

            assertThat(source.getUserId()).isEqualTo(testUser.getId());
        }
    }

    @Nested
    @DisplayName("NewsItem Score Fields")
    class NewsItemScoreTests {

        @Test
        @DisplayName("NewsItem should have score and scoreReasoning fields")
        void newsItemShouldHaveScoreFields() {
            DayBrief brief = createTestDayBrief("Test Brief");
            Source source = createTestSource(brief, "Test Feed");

            NewsItem item = NewsItem.builder()
                    .source(source)
                    .guid("test-guid")
                    .title("Test Article")
                    .link("http://example.com/article")
                    .publishedAt(Instant.now())
                    .status(NewsItemStatus.DONE)
                    .score(85)
                    .scoreReasoning("Highly relevant to AI interests")
                    .build();
            item = newsItemRepository.save(item);

            NewsItem saved = newsItemRepository.findById(item.getId()).orElseThrow();
            assertThat(saved.getScore()).isEqualTo(85);
            assertThat(saved.getScoreReasoning()).isEqualTo("Highly relevant to AI interests");
        }
    }

    @Nested
    @DisplayName("Briefing Items Endpoint")
    class BriefingItemsTests {

        @Test
        @DisplayName("Should list items for briefing")
        void shouldListItemsForBriefing() throws Exception {
            DayBrief brief = createTestDayBrief("Test Brief");
            Source source = createTestSource(brief, "Test Feed");

            // Create test items
            NewsItem item1 = NewsItem.builder()
                    .source(source)
                    .guid("item-1")
                    .title("Test Article 1")
                    .link("http://example.com/article1")
                    .publishedAt(Instant.now())
                    .status(NewsItemStatus.DONE)
                    .score(80)
                    .build();
            newsItemRepository.save(item1);

            NewsItem item2 = NewsItem.builder()
                    .source(source)
                    .guid("item-2")
                    .title("Test Article 2")
                    .link("http://example.com/article2")
                    .publishedAt(Instant.now())
                    .status(NewsItemStatus.DONE)
                    .score(60)
                    .build();
            newsItemRepository.save(item2);

            mockMvc.perform(get(BRIEFS_URL + "/" + brief.getId() + "/items")
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].score").exists());
        }

        @Test
        @DisplayName("Should not show items from other briefings")
        void shouldNotShowItemsFromOtherBriefings() throws Exception {
            DayBrief brief1 = createTestDayBrief("Brief 1");
            DayBrief brief2 = createTestDayBrief("Brief 2");
            Source source1 = createTestSource(brief1, "Feed 1");
            Source source2 = createTestSource(brief2, "Feed 2");

            // Create item in brief1's source
            NewsItem item1 = NewsItem.builder()
                    .source(source1)
                    .guid("item-1")
                    .title("Brief 1 Article")
                    .link("http://example.com/article1")
                    .publishedAt(Instant.now())
                    .status(NewsItemStatus.DONE)
                    .build();
            newsItemRepository.save(item1);

            // Create item in brief2's source
            NewsItem item2 = NewsItem.builder()
                    .source(source2)
                    .guid("item-2")
                    .title("Brief 2 Article")
                    .link("http://example.com/article2")
                    .publishedAt(Instant.now())
                    .status(NewsItemStatus.DONE)
                    .build();
            newsItemRepository.save(item2);

            // Request items for brief1 - should only see item1
            mockMvc.perform(get(BRIEFS_URL + "/" + brief1.getId() + "/items")
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].title").value("Brief 1 Article"));
        }
    }

    private DayBrief createTestDayBrief(String title) {
        // Get next position for this user
        long existingCount = dayBriefRepository.findByUserId(testUser.getId(), org.springframework.data.domain.Pageable.unpaged()).getTotalElements();
        DayBrief dayBrief = DayBrief.builder()
                .userId(testUser.getId())
                .title(title)
                .briefing("Test briefing criteria")
                .frequency(BriefingFrequency.DAILY)
                .scheduleTime(LocalTime.of(8, 0))
                .timezone("UTC")
                .status(DayBriefStatus.ACTIVE)
                .position((int) existingCount)
                .build();
        return dayBriefRepository.save(dayBrief);
    }

    private Source createTestSource(DayBrief dayBrief, String name) {
        Source source = Source.builder()
                .dayBrief(dayBrief)
                .name(name)
                .url("http://example.com/" + name.replace(" ", "-").toLowerCase() + ".xml")
                .type(SourceType.RSS)
                .status(SourceStatus.ACTIVE)
                .build();
        return sourceRepository.save(source);
    }

    private NewsItem createTestNewsItem(Source source, String guid, String title, Integer score) {
        NewsItem item = NewsItem.builder()
                .source(source)
                .guid(guid)
                .title(title)
                .link("http://example.com/" + guid)
                .publishedAt(Instant.now().minusSeconds(3600)) // Set to past to ensure visibility
                .status(NewsItemStatus.DONE)
                .summary("Test summary for " + title)
                .score(score)
                .scoreReasoning(score != null ? "Relevance score based on briefing criteria" : null)
                .build();
        return newsItemRepository.save(item);
    }

    @Nested
    @DisplayName("News Item Operations")
    class NewsItemOperationsTests {

        @Test
        @DisplayName("Should toggle read status on news item")
        void shouldToggleReadStatus() throws Exception {
            DayBrief brief = createTestDayBrief("Test Brief");
            Source source = createTestSource(brief, "Test Feed");
            NewsItem item = createTestNewsItem(source, "item-1", "Test Article", 75);

            // Initially unread
            assertThat(item.getReadAt()).isNull();

            // Toggle read
            mockMvc.perform(patch("/news/" + item.getId() + "/read")
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.readAt").exists());

            // Toggle back to unread
            mockMvc.perform(patch("/news/" + item.getId() + "/read")
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.readAt").doesNotExist());
        }

        @Test
        @DisplayName("Should toggle saved status on news item")
        void shouldToggleSavedStatus() throws Exception {
            DayBrief brief = createTestDayBrief("Test Brief");
            Source source = createTestSource(brief, "Test Feed");
            NewsItem item = createTestNewsItem(source, "item-1", "Test Article", 75);

            // Initially not saved
            assertThat(item.getSaved()).isFalse();

            // Toggle saved
            mockMvc.perform(patch("/news/" + item.getId() + "/saved")
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.saved").value(true));

            // Toggle back to unsaved
            mockMvc.perform(patch("/news/" + item.getId() + "/saved")
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.saved").value(false));
        }

        @Test
        @DisplayName("Should return 404 for other user's news item")
        void shouldReturn404ForOtherUsersNewsItem() throws Exception {
            // Create another user and their data
            User otherUser = User.builder()
                    .username("otheruser2")
                    .email("other2@example.com")
                    .password("password123")
                    .name("Other User")
                    .build();
            otherUser = userRepository.save(otherUser);

            DayBrief otherBrief = DayBrief.builder()
                    .userId(otherUser.getId())
                    .title("Other Brief")
                    .briefing("Other criteria")
                    .frequency(BriefingFrequency.DAILY)
                    .scheduleTime(LocalTime.of(8, 0))
                    .timezone("UTC")
                    .status(DayBriefStatus.ACTIVE)
                    .position(0)
                    .build();
            otherBrief = dayBriefRepository.save(otherBrief);

            Source otherSource = Source.builder()
                    .dayBrief(otherBrief)
                    .name("Other Feed")
                    .url("http://other.com/feed.xml")
                    .type(SourceType.RSS)
                    .status(SourceStatus.ACTIVE)
                    .build();
            otherSource = sourceRepository.save(otherSource);

            NewsItem otherItem = createTestNewsItem(otherSource, "other-item", "Other Article", 50);

            // Try to toggle read on other user's item
            mockMvc.perform(patch("/news/" + otherItem.getId() + "/read")
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return score in news item response")
        void shouldReturnScoreInNewsItemResponse() throws Exception {
            DayBrief brief = createTestDayBrief("Test Brief");
            Source source = createTestSource(brief, "Test Feed");
            createTestNewsItem(source, "item-1", "High Score Article", 95);
            createTestNewsItem(source, "item-2", "Low Score Article", 25);

            mockMvc.perform(get(BRIEFS_URL + "/" + brief.getId() + "/items")
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].score").isNumber())
                    .andExpect(jsonPath("$.content[0].scoreReasoning").exists());
        }
    }

    @Nested
    @DisplayName("Report Generation")
    class ReportGenerationTests {

        @Test
        @DisplayName("Should execute briefing and create report with scored items")
        void shouldExecuteBriefingAndCreateReport() throws Exception {
            DayBrief brief = createTestDayBrief("Tech Brief");
            Source source = createTestSource(brief, "Tech Feed");

            // Create scored items
            createTestNewsItem(source, "item-1", "AI Breakthrough", 90);
            createTestNewsItem(source, "item-2", "ML Framework", 75);
            createTestNewsItem(source, "item-3", "Random News", 30);

            // Flush and clear to ensure data is visible via JPA queries
            entityManager.flush();
            entityManager.clear();

            mockMvc.perform(post(BRIEFS_URL + "/" + brief.getId() + "/execute")
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.dayBriefId").value(brief.getId().toString()))
                    .andExpect(jsonPath("$.dayBriefTitle").value("Tech Brief"))
                    .andExpect(jsonPath("$.status").value("GENERATED"))
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.items.length()").value(3))
                    .andExpect(jsonPath("$.items[0].position").value(1));

            // Verify briefing lastExecutedAt was updated
            DayBrief updated = dayBriefRepository.findById(brief.getId()).orElseThrow();
            assertThat(updated.getLastExecutedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should order report items by score descending")
        void shouldOrderReportItemsByScoreDescending() throws Exception {
            DayBrief brief = createTestDayBrief("Test Brief");
            Source source = createTestSource(brief, "Test Feed");

            // Create items with different scores
            createTestNewsItem(source, "low", "Low Score", 20);
            createTestNewsItem(source, "high", "High Score", 95);
            createTestNewsItem(source, "mid", "Mid Score", 50);

            // Flush and clear to ensure data is visible via JPA queries
            entityManager.flush();
            entityManager.clear();

            MvcResult result = mockMvc.perform(post(BRIEFS_URL + "/" + brief.getId() + "/execute")
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andReturn();

            String response = result.getResponse().getContentAsString();
            // First item should be the highest scored
            assertThat(response).contains("\"position\":1");
            assertThat(response).contains("High Score");
        }

        @Test
        @DisplayName("Should list reports for briefing")
        void shouldListReportsForBriefing() throws Exception {
            DayBrief brief = createTestDayBrief("Test Brief");
            Source source = createTestSource(brief, "Test Feed");
            createTestNewsItem(source, "item-1", "Test Article", 80);

            // Flush and clear to ensure data is visible via JPA queries
            entityManager.flush();
            entityManager.clear();

            // Execute briefing twice to create two reports
            mockMvc.perform(post(BRIEFS_URL + "/" + brief.getId() + "/execute")
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk());

            // Update lastExecutedAt to allow another report
            DayBrief updated = dayBriefRepository.findById(brief.getId()).orElseThrow();
            updated.setLastExecutedAt(Instant.now().minusSeconds(2 * 24 * 3600));
            dayBriefRepository.save(updated);

            mockMvc.perform(post(BRIEFS_URL + "/" + brief.getId() + "/execute")
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk());

            // List reports
            mockMvc.perform(get(BRIEFS_URL + "/" + brief.getId() + "/reports")
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(2));
        }

        @Test
        @DisplayName("Should get specific report by ID")
        void shouldGetSpecificReportById() throws Exception {
            DayBrief brief = createTestDayBrief("Test Brief");
            Source source = createTestSource(brief, "Test Feed");
            createTestNewsItem(source, "item-1", "Test Article", 80);

            // Flush and clear to ensure data is visible via JPA queries
            entityManager.flush();
            entityManager.clear();

            // Execute to create report
            MvcResult executeResult = mockMvc.perform(post(BRIEFS_URL + "/" + brief.getId() + "/execute")
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andReturn();

            String reportId = objectMapper.readTree(executeResult.getResponse().getContentAsString())
                    .get("id").asText();

            // Get the report
            mockMvc.perform(get(BRIEFS_URL + "/" + brief.getId() + "/reports/" + reportId)
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(reportId))
                    .andExpect(jsonPath("$.items").isArray());
        }

        @Test
        @DisplayName("Should return 404 for non-existent briefing")
        void shouldReturn404ForNonExistentBriefing() throws Exception {
            mockMvc.perform(post(BRIEFS_URL + "/" + UUID.randomUUID() + "/execute")
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should create empty report when no scored items")
        void shouldCreateEmptyReportWhenNoScoredItems() throws Exception {
            DayBrief brief = createTestDayBrief("Empty Brief");
            // No sources, no items

            mockMvc.perform(post(BRIEFS_URL + "/" + brief.getId() + "/execute")
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("GENERATED"))
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.items.length()").value(0));
        }
    }

    @Nested
    @DisplayName("DayBrief Update and Delete")
    class DayBriefUpdateDeleteTests {

        @Test
        @DisplayName("Should update DayBrief")
        void shouldUpdateDayBrief() throws Exception {
            DayBrief brief = createTestDayBrief("Original Title");

            DayBriefDTO updateRequest = DayBriefDTO.builder()
                    .title("Updated Title")
                    .briefing("Updated briefing criteria")
                    .frequency(BriefingFrequency.WEEKLY)
                    .scheduleTime(LocalTime.of(9, 30))
                    .timezone("America/New_York")
                    .build();

            mockMvc.perform(put(BRIEFS_URL + "/" + brief.getId())
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("Updated Title"))
                    .andExpect(jsonPath("$.briefing").value("Updated briefing criteria"))
                    .andExpect(jsonPath("$.frequency").value("WEEKLY"));
        }

        @Test
        @DisplayName("Should hard delete DayBrief and cascade to sources")
        void shouldHardDeleteDayBrief() throws Exception {
            DayBrief brief = createTestDayBrief("To Delete");
            Source source = createTestSource(brief, "Delete Me");
            createTestNewsItem(source, "item-1", "Delete Article", 50);

            UUID briefId = brief.getId();
            UUID sourceId = source.getId();

            mockMvc.perform(delete(BRIEFS_URL + "/" + briefId)
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isNoContent());

            // Flush and clear to see database cascade results (not JPA cache)
            entityManager.flush();
            entityManager.clear();

            // Verify hard delete (data no longer exists)
            assertThat(dayBriefRepository.findById(briefId)).isEmpty();
            // Source is also deleted (cascade)
            assertThat(sourceRepository.findById(sourceId)).isEmpty();
        }

        @Test
        @DisplayName("Should pause and resume DayBrief")
        void shouldPauseAndResumeDayBrief() throws Exception {
            DayBrief brief = createTestDayBrief("Pausable Brief");

            // Pause
            DayBriefDTO pauseRequest = DayBriefDTO.builder()
                    .status(DayBriefStatus.PAUSED)
                    .build();

            mockMvc.perform(put(BRIEFS_URL + "/" + brief.getId())
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(pauseRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PAUSED"));

            // Resume
            DayBriefDTO resumeRequest = DayBriefDTO.builder()
                    .status(DayBriefStatus.ACTIVE)
                    .build();

            mockMvc.perform(put(BRIEFS_URL + "/" + brief.getId())
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(resumeRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }
    }

    @Nested
    @DisplayName("Source Update and Delete")
    class SourceUpdateDeleteTests {

        @Test
        @DisplayName("Should update source")
        void shouldUpdateSource() throws Exception {
            DayBrief brief = createTestDayBrief("Test Brief");
            Source source = createTestSource(brief, "Original Name");

            String updateJson = """
                    {
                        "name": "Updated Name",
                        "tags": ["tech", "ai"]
                    }
                    """;

            mockMvc.perform(put(SOURCES_URL + "/" + source.getId())
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Updated Name"))
                    .andExpect(jsonPath("$.tags[0]").value("tech"));
        }

        @Test
        @DisplayName("Should delete source")
        void shouldDeleteSource() throws Exception {
            DayBrief brief = createTestDayBrief("Test Brief");
            Source source = createTestSource(brief, "To Delete");
            UUID sourceId = source.getId();

            mockMvc.perform(delete(SOURCES_URL + "/" + sourceId)
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isNoContent());

            assertThat(sourceRepository.findById(sourceId)).isEmpty();
        }

        @Test
        @DisplayName("Should pause and resume source")
        void shouldPauseAndResumeSource() throws Exception {
            DayBrief brief = createTestDayBrief("Test Brief");
            Source source = createTestSource(brief, "Pausable");

            // Pause
            mockMvc.perform(put(SOURCES_URL + "/" + source.getId())
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"PAUSED\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PAUSED"));

            // Resume
            mockMvc.perform(put(SOURCES_URL + "/" + source.getId())
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"ACTIVE\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }
    }

    @Nested
    @DisplayName("Brief Ordering")
    class BriefOrderingTests {

        @Test
        @DisplayName("Should list briefs ordered by position")
        void shouldListBriefsOrderedByPosition() throws Exception {
            // Create briefs - they should get positions 0, 1, 2
            DayBrief brief1 = createTestDayBrief("Brief A");
            DayBrief brief2 = createTestDayBrief("Brief B");
            DayBrief brief3 = createTestDayBrief("Brief C");

            mockMvc.perform(get(BRIEFS_URL)
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(3))
                    .andExpect(jsonPath("$.content[0].title").value("Brief A"))
                    .andExpect(jsonPath("$.content[0].position").value(0))
                    .andExpect(jsonPath("$.content[1].title").value("Brief B"))
                    .andExpect(jsonPath("$.content[1].position").value(1))
                    .andExpect(jsonPath("$.content[2].title").value("Brief C"))
                    .andExpect(jsonPath("$.content[2].position").value(2));
        }

        @Test
        @DisplayName("Should reorder briefs and persist new order")
        void shouldReorderBriefsAndPersistNewOrder() throws Exception {
            DayBrief brief1 = createTestDayBrief("Brief A");
            DayBrief brief2 = createTestDayBrief("Brief B");
            DayBrief brief3 = createTestDayBrief("Brief C");

            // Reorder: C, A, B
            String reorderJson = String.format("""
                    {
                        "briefIds": ["%s", "%s", "%s"]
                    }
                    """, brief3.getId(), brief1.getId(), brief2.getId());

            mockMvc.perform(post(BRIEFS_URL + "/reorder")
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(reorderJson))
                    .andExpect(status().isNoContent());

            // Verify new order
            mockMvc.perform(get(BRIEFS_URL)
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].title").value("Brief C"))
                    .andExpect(jsonPath("$.content[0].position").value(0))
                    .andExpect(jsonPath("$.content[1].title").value("Brief A"))
                    .andExpect(jsonPath("$.content[1].position").value(1))
                    .andExpect(jsonPath("$.content[2].title").value("Brief B"))
                    .andExpect(jsonPath("$.content[2].position").value(2));
        }

        @Test
        @DisplayName("Should return 400 when reorder request is missing briefs")
        void shouldReturn400WhenReorderRequestMissingBriefs() throws Exception {
            DayBrief brief1 = createTestDayBrief("Brief A");
            DayBrief brief2 = createTestDayBrief("Brief B");

            // Only include one brief instead of both
            String reorderJson = String.format("""
                    {
                        "briefIds": ["%s"]
                    }
                    """, brief1.getId());

            mockMvc.perform(post(BRIEFS_URL + "/reorder")
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(reorderJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when reorder request has invalid brief ID")
        void shouldReturn400WhenReorderRequestHasInvalidBriefId() throws Exception {
            DayBrief brief1 = createTestDayBrief("Brief A");

            // Include a random UUID that doesn't exist
            String reorderJson = String.format("""
                    {
                        "briefIds": ["%s", "%s"]
                    }
                    """, brief1.getId(), UUID.randomUUID());

            mockMvc.perform(post(BRIEFS_URL + "/reorder")
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(reorderJson))
                    .andExpect(status().isBadRequest());
        }
    }
}
