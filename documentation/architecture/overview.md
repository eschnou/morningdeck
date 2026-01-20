# Architecture Overview

Morning Deck is a full-stack application for generating personalized daily news briefings with AI-powered enrichment.

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Frontend (React)                                │
│                    React + TypeScript + Vite + Tailwind                     │
│                              Port 5173 (dev)                                │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      │ REST API (/api/**)
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Backend (Spring Boot)                               │
│                     Java 21 + Spring Boot 3 + Maven                         │
│                              Port 3000                                       │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│  │ Controllers │  │  Services   │  │   Queues    │  │    Jobs     │        │
│  │   (REST)    │  │  (Logic)    │  │  (Workers)  │  │ (Scheduled) │        │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘        │
│         │                │                │                │                │
│         └────────────────┴────────────────┴────────────────┘                │
│                                   │                                          │
│                          ┌────────┴────────┐                                │
│                          ▼                 ▼                                │
│                   ┌────────────┐    ┌────────────┐                          │
│                   │ Repository │    │  Provider  │                          │
│                   │   (JPA)    │    │ (External) │                          │
│                   └────────────┘    └────────────┘                          │
└─────────────────────────────────────────────────────────────────────────────┘
           │                                    │
           ▼                                    ▼
    ┌─────────────┐                 ┌─────────────────────────────┐
    │ PostgreSQL  │                 │      External Services      │
    │   Database  │                 │  OpenAI, SES, S3, Reddit    │
    └─────────────┘                 └─────────────────────────────┘
           │
           ▼
    ┌─────────────┐
    │ Meilisearch │ (optional)
    │   Search    │
    └─────────────┘
```

## Backend Package Structure

```
backend/src/main/java/be/transcode/morningdeck/server/
├── config/             # Spring configurations
│   ├── SecurityConfig.java
│   ├── AsyncConfig.java
│   ├── MeilisearchConfig.java
│   └── ...
│
├── core/               # Main business logic
│   ├── controller/     # REST API endpoints
│   ├── service/        # Business logic services
│   ├── model/          # JPA entities
│   ├── repository/     # Spring Data JPA repositories
│   ├── dto/            # Data transfer objects
│   ├── queue/          # Queue interfaces and workers
│   ├── job/            # Scheduled jobs
│   ├── search/         # Meilisearch integration
│   └── exception/      # Custom exceptions
│
├── provider/           # External integrations
│   ├── ai/             # AI service (OpenAI)
│   ├── sourcefetch/    # Source fetchers (RSS, Web, Email, Reddit)
│   ├── emailsend/      # Outbound email
│   ├── emailreceive/   # Inbound email
│   ├── storage/        # File storage (S3/Local)
│   ├── webfetch/       # Web content fetching
│   └── analytics/      # Event tracking
│
└── filter/             # HTTP filters
    ├── JwtAuthenticationFilter.java
    └── MdcFilter.java
```

## Layer Responsibilities

### Controllers (`core/controller/`)

- Handle HTTP requests and responses
- Validate input (via Spring Validation)
- Convert between DTOs and domain entities
- Delegate to services for business logic
- Never contain business logic

### Services (`core/service/`)

- Implement business logic
- Work with domain entities (not DTOs)
- Transaction boundaries
- Channel-agnostic (no HTTP concerns)
- Coordinate between repositories and providers

### Repositories (`core/repository/`)

- Spring Data JPA repositories
- Data access layer
- Custom queries via `@Query` annotation
- Only for aggregate roots

### Providers (`provider/`)

- External service integrations
- Interface + implementation pattern
- Easily swappable (e.g., S3 vs Local storage)
- Isolated from core business logic

### Queues & Workers (`core/queue/`)

- In-memory job queues
- Background processing
- Status-based workflows
- See [Queue System](./queue-system.md)

### Jobs (`core/job/`)

- `@Scheduled` cron jobs
- Find work and enqueue to queues
- Recovery jobs for stuck items
- Conditionally enabled via properties

## Data Flow

### Request Flow (API Call)

```
HTTP Request
    │
    ▼
JwtAuthenticationFilter (validate token)
    │
    ▼
Controller (validate input, convert DTO)
    │
    ▼
Service (business logic)
    │
    ▼
Repository (database access)
    │
    ▼
Response (convert to DTO)
```

### Background Processing Flow

```
Scheduler Job
    │ (finds work)
    ▼
Queue.enqueue()
    │
    ▼
Worker.process()
    │ (updates status)
    ▼
Service/Repository (persist results)
```

## Technology Stack

### Backend

| Component | Technology |
|-----------|------------|
| Framework | Spring Boot 3 |
| Language | Java 21 |
| Build | Maven |
| ORM | Spring Data JPA / Hibernate |
| Database | PostgreSQL 16 |
| Migrations | Flyway |
| Auth | Spring Security + JWT |
| API Docs | OpenAPI / Swagger UI |
| Search | Meilisearch (optional) |

### Frontend

| Component | Technology |
|-----------|------------|
| Framework | React 18 |
| Language | TypeScript |
| Build | Vite |
| Styling | Tailwind CSS |
| Components | shadcn/ui |
| State | React Context |
| HTTP | Native fetch API |
| DnD | @dnd-kit |

### External Services

| Service | Purpose |
|---------|---------|
| OpenAI | AI enrichment and scoring |
| AWS SES | Email sending |
| AWS S3 | File storage |
| AWS SQS | Email receiving (optional) |
| Reddit API | Reddit source fetching |
| Amplitude | Analytics (optional) |

## Key Design Decisions

### In-Memory Queues

The application uses in-memory queues rather than external message brokers (Redis, RabbitMQ). This is intentional:
- Simpler deployment (single process)
- Sufficient for expected load
- Recovery jobs handle stuck items
- Trade-off: work lost on restart (acceptable)

### Optional Meilisearch

Meilisearch provides instant search but is optional:
- Falls back to PostgreSQL LIKE queries
- Enables/disables via configuration
- Reduces deployment complexity for simple setups

### Single Transaction Per Request

Each API request or queue job runs in a single transaction:
- Simplifies error handling
- Status updates use `REQUIRES_NEW` for visibility
- Batch operations optimized at repository level

### Provider Abstraction

External services use interface + implementation:
- `AiService` → `SpringAiService`, `MockAiService`
- `StorageProvider` → `S3StorageProvider`, `LocalStorageProvider`
- `EmailSender` → `AWSEmailSender`, `SmtpEmailSender`, `LogsEmailSender`

Enables easy testing and environment-specific configuration.

## Related Documentation

- [Queue System](./queue-system.md) - Background processing
- [Security](./security.md) - Authentication and authorization
- [AI Integration](./ai-integration.md) - AI service abstraction
- [Email Infrastructure](./email-infrastructure.md) - Email sending/receiving
- [Search](./search.md) - Meilisearch integration
