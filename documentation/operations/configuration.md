# Configuration

This document covers all configuration options for Morning Deck.

## Configuration Sources

Properties are loaded in order (later overrides earlier):

1. `application.properties` - Base defaults
2. `application-{profile}.properties` - Profile-specific
3. Environment variables
4. Command line arguments

## Spring Profiles

| Profile | Purpose |
|---------|---------|
| `local` | Local development (logs email, uses Groq API) |
| `docker` | Self-hosted Docker deployment (disabled email, local storage) |
| `production` | Production AWS deployment |

Activate via:
```bash
# Command line
--spring.profiles.active=local

# Environment variable
SPRING_PROFILES_ACTIVE=docker
```

## Database Configuration

```properties
# Connection
spring.datasource.url=jdbc:postgresql://localhost:5432/morningdeck
spring.datasource.username=postgres
spring.datasource.password=postgres

# Connection pool (HikariCP)
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.idle-timeout=300000

# JPA settings
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
```

Environment variables:
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

## Security Configuration

### JWT Settings

```properties
# Secret key (base64-encoded, 256-bit minimum)
spring.security.jwt.secret=${JWT_SECRET}

# Token expiration (milliseconds)
spring.security.jwt.expiration=86400000  # 24 hours
```

### CORS Settings

```properties
# Allowed origins (comma-separated, or empty for all in dev)
application.cors.allowed-origins=https://yourdomain.com,https://www.yourdomain.com
```

### Beta Mode

```properties
# Require invite code for registration
application.closed-beta=false
```

## Email Configuration

### Sending

```properties
# Sender implementation: aws, smtp, logs
application.email.sender=logs

# From address
application.email.from=noreply@morningdeck.com

# Display name
application.display-name=Morning Deck

# Email verification
application.email.verification.enabled=true
```

#### AWS SES

```properties
application.email.sender=aws
# Uses default AWS credentials chain
```

#### SMTP

```properties
application.email.sender=smtp
spring.mail.host=smtp.example.com
spring.mail.port=587
spring.mail.username=user
spring.mail.password=pass
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
```

### Receiving

```properties
# Provider: sqs, imap
application.email.provider=sqs

# SQS configuration
application.email.sqs.queue-name=incoming-emails

# S3 for raw email storage
application.email.s3.bucket-name=email-storage

# IMAP configuration (alternative)
application.email.imap.host=imap.example.com
application.email.imap.port=993
application.email.imap.username=user
application.email.imap.password=pass
```

## AI Configuration

The backend uses Spring AI with OpenAI-compatible APIs. It supports OpenAI, OpenRouter, Ollama, or any OpenAI-compatible endpoint.

```properties
# API connection
spring.ai.openai.api-key=${OPENAI_API_KEY}
spring.ai.openai.base-url=${OPENAI_BASE_URL:https://api.openai.com}

# Model for Spring AI calls
spring.ai.openai.chat.options.model=${OPENAI_MODEL_LITE:gpt-4o-mini}

# Model tiers for different tasks
application.ai.models.lite=${OPENAI_MODEL_LITE:gpt-4o-mini}   # Summaries, categorization
application.ai.models.heavy=${OPENAI_MODEL_HEAVY:gpt-4o}     # Briefing generation

# Retry settings
spring.ai.retry.max-attempts=3
spring.ai.retry.backoff.initial-interval=2s
spring.ai.retry.backoff.multiplier=2
```

### Alternative LLM Providers

```bash
# OpenAI (default)
OPENAI_BASE_URL=https://api.openai.com
OPENAI_MODEL_LITE=gpt-4o-mini
OPENAI_MODEL_HEAVY=gpt-4o

# OpenRouter
OPENAI_BASE_URL=https://openrouter.ai/api/v1
OPENAI_MODEL_LITE=anthropic/claude-3-haiku
OPENAI_MODEL_HEAVY=anthropic/claude-3.5-sonnet

# Ollama (local)
OPENAI_BASE_URL=http://localhost:11434/v1
OPENAI_MODEL_LITE=llama3.2
OPENAI_MODEL_HEAVY=llama3.2

# Groq
OPENAI_BASE_URL=https://api.groq.com/openai
OPENAI_MODEL_LITE=llama-3.1-8b-instant
OPENAI_MODEL_HEAVY=llama-3.1-70b-versatile
```

## Search Configuration (Meilisearch)

```properties
# Enable/disable Meilisearch
meilisearch.enabled=true

# Connection
meilisearch.host=http://localhost:7700
meilisearch.api-key=master-key

# Index name
meilisearch.index-name=news_items
```

When disabled, the system falls back to PostgreSQL LIKE queries.

## Storage Configuration

```properties
# Provider: s3, local
application.storage.provider=local

# Local storage path
application.storage.local.path=./uploads

# S3 configuration
application.storage.s3.bucket-name=morningdeck-storage
application.storage.s3.region=us-east-1
```

## Queue Configuration

### Feed Ingestion (FetchQueue)

```properties
# Enable/disable source fetching
application.jobs.feed-ingestion.enabled=true

# Queue capacity
application.jobs.feed-ingestion.queue-capacity=1000

# Worker thread count
application.jobs.feed-ingestion.worker-count=4

# Batch size per scheduler run
application.jobs.feed-ingestion.batch-size=100

# Stuck item threshold (minutes)
application.jobs.feed-ingestion.stuck-threshold-minutes=10
```

### Feed Scheduling

```properties
# Scheduler interval (milliseconds)
application.jobs.feed-scheduling.interval=60000
```

### AI Processing (ProcessingQueue)

```properties
# Enable/disable AI processing
application.jobs.processing.enabled=true

# Queue capacity
application.jobs.processing.queue-capacity=1000

# Worker thread count
application.jobs.processing.worker-count=4

# Scheduler interval (milliseconds)
application.jobs.processing.interval=60000

# Batch size per scheduler run
application.jobs.processing.batch-size=50
```

### Briefing Execution (BriefingQueue)

```properties
# Enable/disable briefing execution
application.jobs.briefing-execution.enabled=true

# Queue capacity
application.jobs.briefing-execution.queue-capacity=100

# Worker thread count
application.jobs.briefing-execution.worker-count=2

# Scheduler interval (milliseconds)
application.jobs.briefing-execution.interval=60000
```

### Recovery Jobs

```properties
# Recovery job interval (milliseconds)
application.jobs.feed-recovery.interval=300000  # 5 minutes
```

## Analytics Configuration

```properties
# Amplitude analytics
amplitude.api-key=${AMPLITUDE_API_KEY:}
```

## AWS Configuration

```properties
# AWS X-Ray tracing
application.aws.xray.enabled=false

# Region (for S3, SES, SQS)
aws.region=us-east-1
```

## Reddit API Configuration

```properties
# Reddit API credentials (for Reddit sources)
reddit.client-id=${REDDIT_CLIENT_ID:}
reddit.client-secret=${REDDIT_CLIENT_SECRET:}
reddit.user-agent=Morning Deck/1.0
```

## Server Configuration

```properties
# Server port
server.port=3000

# Request timeout
server.servlet.session.timeout=30m

# Max upload size
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
```

## Logging Configuration

```properties
# Root level
logging.level.root=INFO

# Application logging
logging.level.be.transcode.morningdeck=INFO

# SQL logging (development)
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE
```

## Actuator Configuration

```properties
# Expose health and info endpoints
management.endpoints.web.exposure.include=health,info

# Show health details
management.endpoint.health.show-details=when_authorized
```

## Environment Variable Mapping

Spring Boot automatically maps environment variables:
- `SPRING_DATASOURCE_URL` → `spring.datasource.url`
- Underscores become dots, uppercase becomes lowercase

Custom mappings:
| Environment Variable | Property |
|---------------------|----------|
| `JWT_SECRET` | `spring.security.jwt.secret` |
| `OPENAI_API_KEY` | `spring.ai.openai.api-key` |
| `OPENAI_BASE_URL` | `spring.ai.openai.base-url` |
| `OPENAI_MODEL_LITE` | `application.ai.models.lite` (and chat model) |
| `OPENAI_MODEL_HEAVY` | `application.ai.models.heavy` |
| `MEILISEARCH_ENABLED` | `meilisearch.enabled` |
| `MEILISEARCH_HOST` | `meilisearch.host` |
| `MEILISEARCH_API_KEY` | `meilisearch.api-key` |

## Configuration Files

| File | Purpose |
|------|---------|
| `application.properties` | Base configuration (production defaults) |
| `application-local.properties` | Local development overrides |
| `application-docker.properties` | Self-hosted Docker deployment |
| `docker-compose.yml` | Local services (PostgreSQL, Meilisearch) |
| `docker-compose.selfhost.yml` | Self-hosted deployment (all services) |
| `.env.selfhost.example` | Example environment for self-hosting |

## Related Documentation

- [Deployment](./deployment.md) - Deployment options
- [Troubleshooting](./troubleshooting.md) - Common issues
- [Local Setup](../development/local-setup.md) - Development environment
