package be.transcode.morningdeck.server.core;

import be.transcode.morningdeck.server.config.TestConfig;
import be.transcode.morningdeck.server.config.TestcontainersConfiguration;
import be.transcode.morningdeck.server.core.dto.AuthResponse;
import be.transcode.morningdeck.server.core.dto.RegisterRequest;
import be.transcode.morningdeck.server.core.model.*;
import be.transcode.morningdeck.server.core.repository.*;
import be.transcode.morningdeck.server.core.service.SubscriptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.Instant;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for credit enforcement across the application.
 * Tests credit deduction, blocking operations when zero credits, and notifications.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
@Import(TestConfig.class)
@ContextConfiguration(initializers = TestcontainersConfiguration.class)
class CreditEnforcementIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private DayBriefRepository dayBriefRepository;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private NewsItemRepository newsItemRepository;

    @Autowired
    private CreditUsageLogRepository creditUsageLogRepository;

    @Autowired
    private SubscriptionService subscriptionService;

    private static final String AUTH_URL = "/auth";
    private static final String BRIEFS_URL = "/daybriefs";

    private String authToken;
    private User testUser;

    @BeforeEach
    void setUp() throws Exception {
        creditUsageLogRepository.deleteAll();
        newsItemRepository.deleteAll();
        sourceRepository.deleteAll();
        dayBriefRepository.deleteAll();
        subscriptionRepository.deleteAll();
        userRepository.deleteAll();

        // Register a test user
        RegisterRequest registerRequest = RegisterRequest.builder()
                .username("credituser")
                .name("Credit Test User")
                .email("credit@example.com")
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
    }

    @Nested
    @DisplayName("Manual Briefing Execution Credit Checks")
    class ManualBriefingExecutionTests {

        @Test
        @DisplayName("Should return 402 when trying to execute briefing with zero credits")
        void shouldReturn402WhenZeroCredits() throws Exception {
            // Set user's credits to zero
            Subscription subscription = subscriptionRepository.findByUser(testUser).orElseThrow();
            subscription.setCreditsBalance(0);
            subscriptionRepository.save(subscription);

            // Create a briefing
            DayBrief brief = createTestDayBrief("Test Brief");

            // Try to execute - should fail with 402
            mockMvc.perform(post(BRIEFS_URL + "/" + brief.getId() + "/execute")
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isPaymentRequired())
                    .andExpect(jsonPath("$.message").exists());
        }

        @Test
        @DisplayName("Should allow execution when user has credits")
        void shouldAllowExecutionWithCredits() throws Exception {
            // Verify user has credits (default from registration)
            Subscription subscription = subscriptionRepository.findByUser(testUser).orElseThrow();
            assertThat(subscription.getCreditsBalance()).isGreaterThan(0);

            // Create a briefing
            DayBrief brief = createTestDayBrief("Test Brief");

            // Execute - should succeed
            mockMvc.perform(post(BRIEFS_URL + "/" + brief.getId() + "/execute")
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").exists());
        }
    }

    @Nested
    @DisplayName("SubscriptionService Credit Methods")
    class SubscriptionServiceTests {

        @Test
        @DisplayName("hasCredits should return true when balance is positive")
        void hasCredits_shouldReturnTrue_whenBalancePositive() {
            Subscription subscription = subscriptionRepository.findByUser(testUser).orElseThrow();
            subscription.setCreditsBalance(100);
            subscriptionRepository.save(subscription);

            assertThat(subscriptionService.hasCredits(testUser.getId())).isTrue();
        }

        @Test
        @DisplayName("hasCredits should return false when balance is zero")
        void hasCredits_shouldReturnFalse_whenBalanceZero() {
            Subscription subscription = subscriptionRepository.findByUser(testUser).orElseThrow();
            subscription.setCreditsBalance(0);
            subscriptionRepository.save(subscription);

            assertThat(subscriptionService.hasCredits(testUser.getId())).isFalse();
        }

        @Test
        @DisplayName("getCreditsBalance should return correct balance")
        void getCreditsBalance_shouldReturnCorrectBalance() {
            Subscription subscription = subscriptionRepository.findByUser(testUser).orElseThrow();
            subscription.setCreditsBalance(42);
            subscriptionRepository.save(subscription);

            assertThat(subscriptionService.getCreditsBalance(testUser.getId())).isEqualTo(42);
        }

        @Test
        @DisplayName("getUserIdsWithCredits should return only users with positive balance")
        void getUserIdsWithCredits_shouldReturnUsersWithCredits() {
            // Set test user's credits to positive
            Subscription subscription = subscriptionRepository.findByUser(testUser).orElseThrow();
            subscription.setCreditsBalance(100);
            subscriptionRepository.save(subscription);

            // Create another user with zero credits
            User zeroCreditsUser = User.builder()
                    .username("zerocredits")
                    .email("zero@example.com")
                    .password("password")
                    .name("Zero Credits User")
                    .build();
            zeroCreditsUser = userRepository.save(zeroCreditsUser);

            Subscription zeroSub = Subscription.builder()
                    .user(zeroCreditsUser)
                    .plan(Subscription.SubscriptionPlan.FREE)
                    .creditsBalance(0)
                    .monthlyCredits(900)
                    .autoRenew(true)
                    .nextRenewalDate(Instant.now().plusSeconds(86400 * 30))
                    .build();
            subscriptionRepository.save(zeroSub);

            Set<UUID> usersWithCredits = subscriptionService.getUserIdsWithCredits();

            assertThat(usersWithCredits).contains(testUser.getId());
            assertThat(usersWithCredits).doesNotContain(zeroCreditsUser.getId());
        }

        @Test
        @DisplayName("useCredits should deduct credits and create usage log")
        void useCredits_shouldDeductCreditsAndLog() {
            Subscription subscription = subscriptionRepository.findByUser(testUser).orElseThrow();
            int initialBalance = 100;
            subscription.setCreditsBalance(initialBalance);
            subscriptionRepository.save(subscription);

            boolean success = subscriptionService.useCredits(testUser.getId(), 1);

            assertThat(success).isTrue();

            // Verify balance decreased
            Subscription updated = subscriptionRepository.findByUser(testUser).orElseThrow();
            assertThat(updated.getCreditsBalance()).isEqualTo(initialBalance - 1);

            // Verify usage log created
            var logs = creditUsageLogRepository.findAll();
            assertThat(logs).hasSize(1);
            assertThat(logs.get(0).getCreditsUsed()).isEqualTo(1);
            assertThat(logs.get(0).getUser().getId()).isEqualTo(testUser.getId());
        }

        @Test
        @DisplayName("useCredits should return false when insufficient balance")
        void useCredits_shouldReturnFalse_whenInsufficientBalance() {
            Subscription subscription = subscriptionRepository.findByUser(testUser).orElseThrow();
            subscription.setCreditsBalance(0);
            subscriptionRepository.save(subscription);

            boolean success = subscriptionService.useCredits(testUser.getId(), 1);

            assertThat(success).isFalse();

            // Verify no usage log created
            var logs = creditUsageLogRepository.findAll();
            assertThat(logs).isEmpty();
        }
    }

    @Nested
    @DisplayName("Repository Query Tests")
    class RepositoryQueryTests {

        @Test
        @DisplayName("findCreditsBalanceByUserId should return balance")
        void findCreditsBalanceByUserId_shouldReturnBalance() {
            Subscription subscription = subscriptionRepository.findByUser(testUser).orElseThrow();
            subscription.setCreditsBalance(555);
            subscriptionRepository.save(subscription);

            var balance = subscriptionRepository.findCreditsBalanceByUserId(testUser.getId());

            assertThat(balance).isPresent();
            assertThat(balance.get()).isEqualTo(555);
        }

        @Test
        @DisplayName("findCreditsBalanceByUserId should return empty for non-existent user")
        void findCreditsBalanceByUserId_shouldReturnEmpty_forNonExistentUser() {
            var balance = subscriptionRepository.findCreditsBalanceByUserId(UUID.randomUUID());

            assertThat(balance).isEmpty();
        }

        @Test
        @DisplayName("findUserIdsWithCredits should return correct users")
        void findUserIdsWithCredits_shouldReturnCorrectUsers() {
            Subscription subscription = subscriptionRepository.findByUser(testUser).orElseThrow();
            subscription.setCreditsBalance(100);
            subscriptionRepository.save(subscription);

            Set<UUID> userIds = subscriptionRepository.findUserIdsWithCredits();

            assertThat(userIds).contains(testUser.getId());
        }
    }

    private DayBrief createTestDayBrief(String title) {
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
                .publishedAt(Instant.now().minusSeconds(3600))
                .status(NewsItemStatus.DONE)
                .summary("Test summary for " + title)
                .score(score)
                .build();
        return newsItemRepository.save(item);
    }
}
