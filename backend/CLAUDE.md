# Backend - Spring Boot Service

Spring Boot 3 backend (Java 21) with Maven. For project-wide context, see root `CLAUDE.md`.

## Commands

```bash
mvn clean compile                    # Compile sources
mvn clean package                    # Build JAR
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"  # Run locally (port 3000)
mvn test                             # Unit tests
mvn test -Dtest=UserIT               # Single test class
mvn verify                           # All tests + verification
```

## Architecture

### Package Structure

```
src/main/java/
├── config/         # Spring configurations (Security, AWS, Web, Async, Meilisearch)
├── core/           # Main business logic
│   ├── controller/ # REST endpoints
│   ├── service/    # Business services
│   ├── model/      # JPA entities
│   ├── repository/ # Spring Data JPA repositories
│   ├── dto/        # Data transfer objects
│   ├── search/     # Meilisearch integration (search, sync, indexing)
│   └── exception/  # Custom exceptions and GlobalExceptionHandler
├── filter/         # HTTP filters (JWT, MDC, X-Ray)
└── provider/       # External service integrations
    ├── emailsend/  # Email sending (AWS SES, SMTP)
    ├── emailreceive/ # Inbound email (IMAP, SQS)
    ├── storage/    # File storage (S3, Local)
    └── analytics/  # Event tracking (Amplitude)
```

### Design Patterns

**Service Layer**
- Services are channel-agnostic - never reference DTOs, only domain entities
- Return domain entities; controllers handle DTO conversion
- Pass primitive parameters to services, not DTOs
- Business validation belongs in service layer

**JPA Relationships**
- Use proper JPA relationships instead of separate services for related entities
- Leverage cascade operations for related entity persistence
- Only create repositories for aggregate roots
- Use `@Enumerated(EnumType.STRING)` for enums - never ORDINAL

**REST API Design**
- Single DTO for request/response (more RESTful)
- Use `@JsonInclude(NON_NULL)` on all DTOs
- Include related data directly rather than forcing multiple API calls

**Domain Boundaries**
- Controllers: DTOs, validation, HTTP concerns
- Services: Domain logic, entities, channel-agnostic
- Repositories: Data persistence, JPA relationships
- Entities: Domain models, business rules

### API Endpoints

- `/auth/**` - Public authentication (register, login)
- `/users/**` - User profile management
- `/subscription/**` - Subscription/credits management
- `/daybriefs/**` - DayBrief management and reports
- `/sources/**` - News source management
- `/news/**` - News item search and retrieval
- `/admin/**` - Admin endpoints
- `/swagger-ui.html`, `/api-docs` - OpenAPI documentation

### Key Integrations

- **Database**: PostgreSQL (production), H2 (tests)
- **Migrations**: Flyway (`src/main/resources/db/migrations/`)
  - **NEVER modify a migration file that has been committed** - Flyway uses checksums to validate migrations; any change (even comments) will cause startup failures on databases where the migration was already applied
  - To fix a committed migration, create a new migration file instead
- **Auth**: JWT tokens via JwtService, Spring Security
- **Storage**: StorageProvider interface (S3/Local implementations)
- **Email**: Freemarker templates in `src/main/resources/templates/email/`
- **Search**: Meilisearch (optional) - instant search for articles

## Configuration

### Profiles
- `local` - Development (port 3000, local storage, localhost DB)
- Default - Production (port 8080, AWS services, external DB)

### Environment Variables (Production)
```
DB_HOST, DB_NAME, DB_USERNAME, DB_PASSWORD
AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY
S3_FILES_BUCKET
APP_DOMAIN, APP_URL

# Meilisearch (optional - search disabled if not set)
MEILISEARCH_ENABLED=true
MEILISEARCH_HOST=http://localhost:7700
MEILISEARCH_API_KEY=your-secure-key
```

## Testing

- Integration tests use suffix `IT.java` (e.g., `UserIT.java`)
- Tests run against H2 in-memory with Flyway migrations
- Tests are transactional for isolation
- Use JUnit 5 and Mockito

## Java Coding Guidelines

### Style
- Java 21 features encouraged
- Use Lombok: `@Data`, `@Builder`, `@RequiredArgsConstructor`
- Builder pattern for complex objects
- Static factory methods for common operations
- CamelCase naming; test classes: `*Test` or `*IT`

### DTOs
```java
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MyDTO {
    private String name;
    private UUID optionalId; // Omitted from JSON if null
}
```

### Error Handling
- Use exceptions for error conditions
- Add custom exceptions to GlobalExceptionHandler
- Return appropriate HTTP status codes
