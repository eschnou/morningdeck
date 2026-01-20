# Morning Deck Documentation

Technical documentation for the Morning Deck application - an AI-powered news aggregation and briefing platform.

## Quick Links

- [Local Setup](./development/local-setup.md) - Get started with development
- [Architecture Overview](./architecture/overview.md) - High-level system design
- [API Reference](./development/api-reference.md) - REST API endpoints
- [Configuration](./operations/configuration.md) - All configuration options

## Documentation Structure

### Domain

What the system does - core entities and business logic.

| Document | Description |
|----------|-------------|
| [Briefings](./domain/briefings.md) | DayBriefs, scheduling, reports, email delivery |
| [News Items](./domain/news-items.md) | Item lifecycle, AI enrichment, scoring |
| [Sources](./domain/sources.md) | RSS, Web, Email, Reddit source abstraction |
| [Credits](./domain/credits.md) | Credit system, subscription plans, enforcement |
| [Users](./domain/users.md) | Registration, authentication, roles, subscriptions |

### Architecture

How the system is built - technical design and patterns.

| Document | Description |
|----------|-------------|
| [Overview](./architecture/overview.md) | High-level architecture, layers, request flow |
| [AI Integration](./architecture/ai-integration.md) | OpenAI integration, prompts, token tracking |
| [Queue System](./architecture/queue-system.md) | Background processing, workers, schedulers |
| [Security](./architecture/security.md) | JWT authentication, Spring Security, CORS |
| [Email Infrastructure](./architecture/email-infrastructure.md) | Sending (SES/SMTP) and receiving (SQS/IMAP) |
| [Search](./architecture/search.md) | Meilisearch integration and fallback |

### Operations

How to run and maintain the system.

| Document | Description |
|----------|-------------|
| [Deployment](./operations/deployment.md) | Deployment options, AWS architecture, scaling |
| [Configuration](./operations/configuration.md) | All configuration properties documented |
| [Troubleshooting](./operations/troubleshooting.md) | Common issues and solutions |

### Development

How to contribute to the codebase.

| Document | Description |
|----------|-------------|
| [Local Setup](./development/local-setup.md) | Development environment setup |
| [Testing](./development/testing.md) | Test strategy, tools, patterns |
| [API Reference](./development/api-reference.md) | REST endpoint documentation |

## Technology Stack

### Backend

- Java 21
- Spring Boot 3
- Spring Data JPA / PostgreSQL
- Spring Security + JWT
- Flyway migrations
- OpenAI (via Spring AI)
- Meilisearch (optional)

### Frontend

- React 18 + TypeScript
- Vite
- Tailwind CSS
- shadcn/ui
- React Query

### Infrastructure

- PostgreSQL 16
- Meilisearch
- AWS (SES, S3, SQS - optional)

## Key Concepts

### Briefings

Users create **briefings** (DayBriefs) that aggregate news from multiple sources. Each briefing:
- Has a schedule (daily or weekly)
- Contains multiple sources
- Generates reports with top-scored items
- Can deliver reports via email

### Sources

**Sources** are content feeds added to briefings:
- RSS/Atom feeds
- Web pages (scraping)
- Email newsletters
- Reddit subreddits

### News Items

**News items** are individual articles/posts fetched from sources:
- Enriched with AI (summary, tags, score)
- Scored based on briefing context
- Filterable by read status, saved, score

### Credits

Users have **credits** that are consumed by:
- AI processing of news items (1 credit each)
- Briefing execution (1 credit each)

Subscription plans replenish credits monthly.

## Getting Started

1. **Set up locally**: [Local Setup](./development/local-setup.md)
2. **Understand the architecture**: [Overview](./architecture/overview.md)
3. **Explore the API**: [API Reference](./development/api-reference.md)
4. **Configure for production**: [Configuration](./operations/configuration.md)

## Related Resources

- Backend CLAUDE.md: `backend/CLAUDE.md`
- Frontend CLAUDE.md: `frontend/CLAUDE.md`
- Product specs: `specs/product.md`
- OpenAPI docs: `/swagger-ui.html` (when running)
