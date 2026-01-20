package be.transcode.morningdeck.server.core.controller;

import be.transcode.morningdeck.server.config.TestConfig;
import be.transcode.morningdeck.server.config.TestcontainersConfiguration;
import be.transcode.morningdeck.server.core.dto.AuthResponse;
import be.transcode.morningdeck.server.core.dto.RegisterRequest;
import be.transcode.morningdeck.server.core.dto.SourceDTO;
import be.transcode.morningdeck.server.core.model.*;
import be.transcode.morningdeck.server.core.repository.DayBriefRepository;
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

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
@Import(TestConfig.class)
@ContextConfiguration(initializers = TestcontainersConfiguration.class)
class SourceIT {

    private static WireMockServer wireMockServer;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private DayBriefRepository dayBriefRepository;

    private static final String BASE_URL = "/sources";
    private static final String AUTH_URL = "/auth";
    private String authToken;
    private User testUser;
    private DayBrief testDayBrief;

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
                <item>
                  <guid>test-item-2</guid>
                  <title>Test Article 2</title>
                  <link>http://example.com/article2</link>
                  <pubDate>Tue, 02 Jan 2024 00:00:00 GMT</pubDate>
                  <description>Test content for article 2</description>
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

        // Create a test briefing
        testDayBrief = DayBrief.builder()
                .userId(testUser.getId())
                .title("Test Briefing")
                .briefing("Test briefing criteria")
                .frequency(BriefingFrequency.DAILY)
                .scheduleTime(LocalTime.of(8, 0))
                .timezone("UTC")
                .status(DayBriefStatus.ACTIVE)
                .position(0)
                .build();
        testDayBrief = dayBriefRepository.save(testDayBrief);

        // Setup WireMock to return RSS feed
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/feed.xml"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/rss+xml")
                        .withBody(SAMPLE_RSS_FEED)));
    }

    @Nested
    @DisplayName("Create Source Tests")
    class CreateSourceTests {

        @Test
        @DisplayName("Should create source with valid RSS URL")
        void shouldCreateSourceWithValidUrl() throws Exception {
            String feedUrl = wireMockServer.baseUrl() + "/feed.xml";

            SourceDTO request = SourceDTO.builder()
                    .briefingId(testDayBrief.getId())
                    .url(feedUrl)
                    .type(SourceType.RSS)
                    .tags(List.of("tech", "news"))
                    .build();

            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.url").value(feedUrl))
                    .andExpect(jsonPath("$.name").value("Test Feed"))
                    .andExpect(jsonPath("$.type").value("RSS"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.briefingId").value(testDayBrief.getId().toString()))
                    .andExpect(jsonPath("$.tags[0]").value("tech"))
                    .andExpect(jsonPath("$.itemCount").value(0));

            // Verify database
            List<Source> sources = sourceRepository.findAll();
            assertThat(sources).hasSize(1);
            assertThat(sources.get(0).getName()).isEqualTo("Test Feed");
            assertThat(sources.get(0).getUserId()).isEqualTo(testUser.getId());
        }

        @Test
        @DisplayName("Should create source with custom name")
        void shouldCreateSourceWithCustomName() throws Exception {
            String feedUrl = wireMockServer.baseUrl() + "/feed.xml";

            SourceDTO request = SourceDTO.builder()
                    .briefingId(testDayBrief.getId())
                    .url(feedUrl)
                    .name("My Custom Feed")
                    .type(SourceType.RSS)
                    .build();

            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("My Custom Feed"));
        }

        @Test
        @DisplayName("Should fail to create source with duplicate URL in same briefing")
        void shouldFailToCreateDuplicateSource() throws Exception {
            String feedUrl = wireMockServer.baseUrl() + "/feed.xml";

            // Create first source
            SourceDTO request = SourceDTO.builder()
                    .briefingId(testDayBrief.getId())
                    .url(feedUrl)
                    .type(SourceType.RSS)
                    .build();

            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            // Try to create duplicate
            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already exists")));
        }

        @Test
        @DisplayName("Should fail to create source with invalid RSS URL")
        void shouldFailToCreateSourceWithInvalidRss() throws Exception {
            // Setup WireMock to return invalid content
            wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/invalid.xml"))
                    .willReturn(aResponse()
                            .withHeader("Content-Type", "text/plain")
                            .withBody("Not a valid RSS feed")));

            String feedUrl = wireMockServer.baseUrl() + "/invalid.xml";

            SourceDTO request = SourceDTO.builder()
                    .briefingId(testDayBrief.getId())
                    .url(feedUrl)
                    .type(SourceType.RSS)
                    .build();

            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Invalid RSS feed")));
        }

        @Test
        @DisplayName("Should fail to create source without authentication")
        void shouldFailToCreateSourceWithoutAuth() throws Exception {
            SourceDTO request = SourceDTO.builder()
                    .briefingId(testDayBrief.getId())
                    .url("http://example.com/feed.xml")
                    .type(SourceType.RSS)
                    .build();

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should fail to create source with missing URL")
        void shouldFailToCreateSourceWithMissingUrl() throws Exception {
            SourceDTO request = SourceDTO.builder()
                    .briefingId(testDayBrief.getId())
                    .type(SourceType.RSS)
                    .build();

            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should fail to create source without briefingId")
        void shouldFailToCreateSourceWithoutBriefingId() throws Exception {
            String feedUrl = wireMockServer.baseUrl() + "/feed.xml";

            SourceDTO request = SourceDTO.builder()
                    .url(feedUrl)
                    .type(SourceType.RSS)
                    .build();

            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should create EMAIL source with name and generate email address")
        void shouldCreateEmailSourceWithName() throws Exception {
            SourceDTO request = SourceDTO.builder()
                    .briefingId(testDayBrief.getId())
                    .name("My Newsletter")
                    .type(SourceType.EMAIL)
                    .tags(List.of("newsletter"))
                    .build();

            MvcResult result = mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.name").value("My Newsletter"))
                    .andExpect(jsonPath("$.type").value("EMAIL"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.emailAddress").exists())
                    .andExpect(jsonPath("$.briefingId").value(testDayBrief.getId().toString()))
                    .andReturn();

            // Verify email address format (UUID@domain)
            SourceDTO response = objectMapper.readValue(result.getResponse().getContentAsString(), SourceDTO.class);
            assertThat(response.getEmailAddress()).matches("[0-9a-f-]+@.+");

            // Verify database
            List<Source> sources = sourceRepository.findAll();
            assertThat(sources).hasSize(1);
            assertThat(sources.get(0).getName()).isEqualTo("My Newsletter");
            assertThat(sources.get(0).getType()).isEqualTo(SourceType.EMAIL);
            assertThat(sources.get(0).getEmailAddress()).isNotNull();
        }

        @Test
        @DisplayName("Should fail to create EMAIL source without name")
        void shouldFailToCreateEmailSourceWithoutName() throws Exception {
            SourceDTO request = SourceDTO.builder()
                    .briefingId(testDayBrief.getId())
                    .type(SourceType.EMAIL)
                    .build();

            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Name is required")));
        }

        @Test
        @DisplayName("EMAIL source should not require URL")
        void emailSourceShouldNotRequireUrl() throws Exception {
            SourceDTO request = SourceDTO.builder()
                    .briefingId(testDayBrief.getId())
                    .name("My Newsletter")
                    .type(SourceType.EMAIL)
                    // No URL provided
                    .build();

            mockMvc.perform(post(BASE_URL)
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value("EMAIL"))
                    .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.startsWith("email://")));
        }
    }

    @Nested
    @DisplayName("List Sources Tests")
    class ListSourcesTests {

        @Test
        @DisplayName("Should list user sources")
        void shouldListUserSources() throws Exception {
            // Create test sources
            createTestSource("Feed 1", SourceStatus.ACTIVE);
            createTestSource("Feed 2", SourceStatus.ACTIVE);
            createTestSource("Feed 3", SourceStatus.PAUSED);

            mockMvc.perform(get(BASE_URL)
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(3))
                    .andExpect(jsonPath("$.totalElements").value(3));
        }

        @Test
        @DisplayName("Should filter sources by status")
        void shouldFilterSourcesByStatus() throws Exception {
            createTestSource("Feed 1", SourceStatus.ACTIVE);
            createTestSource("Feed 2", SourceStatus.ACTIVE);
            createTestSource("Feed 3", SourceStatus.PAUSED);

            mockMvc.perform(get(BASE_URL)
                            .header("Authorization", "Bearer " + authToken)
                            .param("status", "ACTIVE")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2));
        }

        @Test
        @DisplayName("Should support pagination")
        void shouldSupportPagination() throws Exception {
            for (int i = 0; i < 25; i++) {
                createTestSource("Feed " + i, SourceStatus.ACTIVE);
            }

            mockMvc.perform(get(BASE_URL)
                            .header("Authorization", "Bearer " + authToken)
                            .param("page", "0")
                            .param("size", "10")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(10))
                    .andExpect(jsonPath("$.totalElements").value(25))
                    .andExpect(jsonPath("$.totalPages").value(3));
        }
    }

    @Nested
    @DisplayName("Get Source Tests")
    class GetSourceTests {

        @Test
        @DisplayName("Should get source by ID")
        void shouldGetSourceById() throws Exception {
            Source source = createTestSource("Test Feed", SourceStatus.ACTIVE);

            mockMvc.perform(get(BASE_URL + "/" + source.getId())
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(source.getId().toString()))
                    .andExpect(jsonPath("$.name").value("Test Feed"));
        }

        @Test
        @DisplayName("Should return 404 for non-existent source")
        void shouldReturn404ForNonExistentSource() throws Exception {
            mockMvc.perform(get(BASE_URL + "/" + java.util.UUID.randomUUID())
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Update Source Tests")
    class UpdateSourceTests {

        @Test
        @DisplayName("Should update source name and tags")
        void shouldUpdateSourceNameAndTags() throws Exception {
            Source source = createTestSource("Original Name", SourceStatus.ACTIVE);

            SourceDTO updateRequest = SourceDTO.builder()
                    .name("Updated Name")
                    .tags(List.of("updated", "tags"))
                    .build();

            mockMvc.perform(put(BASE_URL + "/" + source.getId())
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Updated Name"))
                    .andExpect(jsonPath("$.tags[0]").value("updated"));

            // Verify database
            Source updated = sourceRepository.findById(source.getId()).orElseThrow();
            assertThat(updated.getName()).isEqualTo("Updated Name");
        }

        @Test
        @DisplayName("Should update source status to PAUSED")
        void shouldUpdateSourceStatusToPaused() throws Exception {
            Source source = createTestSource("Test Feed", SourceStatus.ACTIVE);

            SourceDTO updateRequest = SourceDTO.builder()
                    .status(SourceStatus.PAUSED)
                    .build();

            mockMvc.perform(put(BASE_URL + "/" + source.getId())
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PAUSED"));
        }
    }

    @Nested
    @DisplayName("Delete Source Tests")
    class DeleteSourceTests {

        @Test
        @DisplayName("Should hard delete source")
        void shouldHardDeleteSource() throws Exception {
            Source source = createTestSource("Test Feed", SourceStatus.ACTIVE);

            mockMvc.perform(delete(BASE_URL + "/" + source.getId())
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isNoContent());

            // Verify hard delete - source no longer exists
            assertThat(sourceRepository.findById(source.getId())).isEmpty();
        }

        @Test
        @DisplayName("Should return 404 when deleting non-existent source")
        void shouldReturn404WhenDeletingNonExistent() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/" + java.util.UUID.randomUUID())
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isNotFound());
        }
    }

    private Source createTestSource(String name, SourceStatus status) {
        Source source = Source.builder()
                .dayBrief(testDayBrief)
                .name(name)
                .url("http://example.com/" + name.replace(" ", "-").toLowerCase() + ".xml")
                .type(SourceType.RSS)
                .status(status)
                .build();
        return sourceRepository.save(source);
    }
}
