# Testing

This document covers the testing strategy and tools for Morning Deck.

## Backend Testing

### Test Stack

| Tool | Purpose |
|------|---------|
| JUnit 5 | Test framework |
| Spring Boot Test | Spring integration |
| Mockito | Mocking |
| AssertJ | Fluent assertions |
| Testcontainers | PostgreSQL, Meilisearch containers |
| WireMock | HTTP mocking |
| GreenMail | Email testing |
| Awaitility | Async testing |

### Running Tests

```bash
# Unit tests only
mvn test

# Single test class
mvn test -Dtest=UserIT

# Single test method
mvn test -Dtest=UserIT#testRegister

# All tests + integration
mvn verify

# Skip tests
mvn package -DskipTests
```

### Test Types

#### Unit Tests (`*Test.java`)

Test individual classes in isolation:

```java
@ExtendWith(MockitoExtension.class)
class NewsItemServiceTest {

    @Mock
    private NewsItemRepository newsItemRepository;

    @InjectMocks
    private NewsItemService newsItemService;

    @Test
    void shouldCalculateScore() {
        // Given
        NewsItem item = NewsItem.builder()
            .title("Test")
            .build();

        // When
        int score = newsItemService.calculateScore(item);

        // Then
        assertThat(score).isBetween(0, 100);
    }
}
```

#### Integration Tests (`*IT.java`)

Test multiple components together with real database:

```java
@SpringBootTest
@Testcontainers
@Transactional
class UserIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private UserService userService;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void shouldCreateUser() {
        // Test with real database
        User user = userService.createUser("test@example.com", "password");
        assertThat(user.getId()).isNotNull();
    }
}
```

### Testcontainers

Integration tests use Testcontainers for:
- **PostgreSQL**: Real database for JPA tests
- **Meilisearch**: Search functionality tests

Containers start automatically and are shared across tests.

### Test Configuration

**File:** `backend/src/test/resources/application.properties`

Tests use H2 in-memory database by default (faster than Testcontainers for simple tests).

### Mocking External Services

#### WireMock for HTTP APIs

```java
@WireMockTest(httpPort = 8089)
class RssFetcherTest {

    @Test
    void shouldFetchRssFeed(WireMockRuntimeInfo wmRuntime) {
        stubFor(get("/feed.xml")
            .willReturn(ok()
                .withHeader("Content-Type", "application/xml")
                .withBody("<rss>...</rss>")));

        // Test RSS fetcher against mock
    }
}
```

#### GreenMail for Email

```java
@RegisterExtension
static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

@Test
void shouldSendEmail() {
    emailService.sendWelcomeEmail(user);

    MimeMessage[] messages = greenMail.getReceivedMessages();
    assertThat(messages).hasSize(1);
    assertThat(messages[0].getSubject()).contains("Welcome");
}
```

### Test Data Builders

Use Builder pattern for test data:

```java
class TestData {
    static User.UserBuilder aUser() {
        return User.builder()
            .email("test@example.com")
            .fullName("Test User")
            .enabled(true);
    }

    static NewsItem.NewsItemBuilder aNewsItem() {
        return NewsItem.builder()
            .title("Test Article")
            .url("https://example.com/article")
            .status(NewsItemStatus.DONE);
    }
}
```

### Async Testing with Awaitility

```java
@Test
void shouldProcessItemAsync() {
    // Trigger async operation
    processingQueue.enqueue(item.getId());

    // Wait for completion
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(() -> {
            NewsItem updated = newsItemRepository.findById(item.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(NewsItemStatus.DONE);
        });
}
```

## Frontend Testing

### Test Stack

The frontend currently relies on:
- **TypeScript** - Compile-time type checking
- **ESLint** - Static analysis

### Running Checks

```bash
# Type checking (via build)
npm run build

# Linting
npm run lint
```

### Manual Testing

For UI testing, use the development server:

```bash
npm run dev
```

Key areas to test manually:
- Authentication flow (register, login, logout)
- Briefing CRUD operations
- Source management
- News item interactions (read, saved)
- Search functionality
- Report viewing

## Test Patterns

### Authentication in Tests

For integration tests requiring authentication:

```java
@WithMockUser(username = "test@example.com", roles = "USER")
@Test
void shouldAccessProtectedEndpoint() {
    // Test as authenticated user
}

@WithMockUser(roles = "ADMIN")
@Test
void shouldAccessAdminEndpoint() {
    // Test as admin
}
```

### Testing REST Controllers

```java
@WebMvcTest(NewsController.class)
class NewsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NewsItemService newsItemService;

    @Test
    @WithMockUser
    void shouldReturnNewsItems() throws Exception {
        when(newsItemService.listItems(any(), any()))
            .thenReturn(Page.empty());

        mockMvc.perform(get("/news"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray());
    }
}
```

### Testing JPA Repositories

```java
@DataJpaTest
class NewsItemRepositoryTest {

    @Autowired
    private NewsItemRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void shouldFindByStatus() {
        NewsItem item = TestData.aNewsItem().build();
        entityManager.persist(item);

        List<NewsItem> found = repository.findByStatus(NewsItemStatus.DONE);

        assertThat(found).contains(item);
    }
}
```

## Coverage

No formal coverage requirements currently. Focus on:
- Critical business logic
- Complex algorithms
- Edge cases
- Error handling

## Test File Organization

```
backend/src/test/java/
├── be/transcode/morningdeck/server/
│   ├── core/
│   │   ├── controller/     # Controller tests
│   │   ├── service/        # Service tests
│   │   └── repository/     # Repository tests
│   ├── provider/           # Provider tests
│   └── integration/        # End-to-end integration tests
└── resources/
    └── application.properties  # Test configuration
```

## CI/CD Testing

Tests run automatically on:
- Pull request creation
- Push to main branch

Pipeline steps:
1. `mvn verify` - Runs all tests
2. Build Docker image (if tests pass)

## Key Test Files

| Component | Test File |
|-----------|-----------|
| User registration | `UserIT.java` |
| News processing | `ProcessingWorkerTest.java` |
| RSS fetching | `RssFetcherTest.java` |
| Email sending | `EmailServiceTest.java` |

## Related Documentation

- [Local Setup](./local-setup.md) - Development environment
- [API Reference](./api-reference.md) - Endpoints to test
