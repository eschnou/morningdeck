# Deployment

This document covers deployment options for Morning Deck, from local development to production.

## Deployment Options

### 1. Local Development

See [Local Setup](../development/local-setup.md) for full development environment setup.

```bash
# Start dependencies
docker-compose up -d

# Backend
cd backend && mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"

# Frontend
cd frontend && npm run dev
```

### 2. Docker Compose (Self-Hosted)

For simple self-hosted deployments, use the included `docker-compose.selfhost.yml`:

```bash
# 1. Copy the example environment file
cp .env.selfhost.example .env

# 2. Edit .env and configure your settings (at minimum, set OPENAI_API_KEY)
nano .env

# 3. Build and start all services
docker compose -f docker-compose.selfhost.yml up -d

# 4. Access the app at http://localhost (or your configured domain)
```

The self-hosted compose file includes:
- **PostgreSQL 16** - Database
- **Backend** - Spring Boot API (built from source with docker profile)
- **Frontend** - React SPA served via nginx (proxies /api to backend)

#### Configuration

All settings are configured via environment variables in `.env`. Key settings:

```bash
# Required: OpenAI API (or compatible provider)
OPENAI_API_KEY=your-api-key
OPENAI_BASE_URL=https://api.openai.com  # Or OpenRouter, Ollama, etc.
OPENAI_MODEL_LITE=gpt-4o-mini
OPENAI_MODEL_HEAVY=gpt-4o

# Database
DB_PASSWORD=changeme-in-production

# Application URL (for CORS and email links)
APP_URL=http://localhost
```

#### Alternative LLM Providers

The backend supports any OpenAI-compatible API:

```bash
# OpenRouter
OPENAI_BASE_URL=https://openrouter.ai/api/v1
OPENAI_MODEL_LITE=anthropic/claude-3-haiku

# Ollama (local)
OPENAI_BASE_URL=http://host.docker.internal:11434/v1
OPENAI_MODEL_LITE=llama3.2
OPENAI_MODEL_HEAVY=llama3.2

# Groq
OPENAI_BASE_URL=https://api.groq.com/openai
OPENAI_MODEL_LITE=llama-3.1-8b-instant
```

#### Self-Host Defaults

The `docker` profile disables features that require additional setup:
- Email verification disabled (users are verified automatically)
- Local file storage (no S3)
- Meilisearch disabled (search falls back to database)
- Email sending disabled (logged only)

See `.env.selfhost.example` for all available options.

### 3. AWS Production

Recommended AWS architecture:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              CloudFront                                  │
│                         (CDN + SSL termination)                         │
└─────────────────────────────────┬───────────────────────────────────────┘
                                  │
                    ┌─────────────┴─────────────┐
                    │                           │
                    ▼                           ▼
          ┌─────────────────┐         ┌─────────────────┐
          │   S3 Bucket     │         │   ALB           │
          │   (Frontend)    │         │   (Backend)     │
          └─────────────────┘         └────────┬────────┘
                                               │
                                               ▼
                                      ┌─────────────────┐
                                      │   ECS Fargate   │
                                      │   (Backend)     │
                                      └────────┬────────┘
                                               │
                          ┌────────────────────┼────────────────────┐
                          │                    │                    │
                          ▼                    ▼                    ▼
                  ┌─────────────┐      ┌─────────────┐     ┌──────────────┐
                  │    RDS      │      │     SES     │     │      S3      │
                  │ PostgreSQL  │      │   (Email)   │     │  (Storage)   │
                  └─────────────┘      └─────────────┘     └──────────────┘
```

## AWS Services Used

| Service | Purpose | Required |
|---------|---------|----------|
| RDS PostgreSQL | Database | Yes |
| ECS Fargate | Backend hosting | Yes (or EC2) |
| S3 | Frontend hosting, file storage | Yes |
| CloudFront | CDN, SSL | Yes |
| SES | Email sending | Yes |
| SQS | Email receiving | Optional |
| Meilisearch Cloud | Search | Optional |
| X-Ray | Distributed tracing | Optional |

## Environment Variables

### Required

```bash
# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/morningdeck
SPRING_DATASOURCE_USERNAME=username
SPRING_DATASOURCE_PASSWORD=password

# Security
JWT_SECRET=base64-encoded-256-bit-secret

# AI
OPENAI_API_KEY=sk-...

# Email sending
APPLICATION_EMAIL_SENDER=aws  # or smtp, logs
APPLICATION_EMAIL_FROM=noreply@yourdomain.com
```

### Optional

```bash
# AWS (if using AWS services)
AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=...
AWS_SECRET_ACCESS_KEY=...

# Meilisearch (if enabled)
MEILISEARCH_ENABLED=true
MEILISEARCH_HOST=http://meilisearch:7700
MEILISEARCH_API_KEY=master-key

# Email receiving (if using SQS)
APPLICATION_EMAIL_PROVIDER=sqs
APPLICATION_EMAIL_SQS_QUEUE_NAME=incoming-emails
APPLICATION_EMAIL_S3_BUCKET_NAME=email-storage

# CORS (production)
APPLICATION_CORS_ALLOWED_ORIGINS=https://yourdomain.com

# Closed beta mode
APPLICATION_CLOSED_BETA=false

# Analytics
AMPLITUDE_API_KEY=...
```

## Database Setup

### RDS Configuration

Recommended settings:
- Engine: PostgreSQL 16
- Instance: db.t3.micro (dev) / db.t3.small (prod)
- Storage: 20GB gp3 (expandable)
- Multi-AZ: Yes for production

### Migrations

Flyway runs automatically on startup. For manual control:

```bash
# Check migration status
mvn flyway:info -Dflyway.url=jdbc:postgresql://host/db

# Run pending migrations
mvn flyway:migrate -Dflyway.url=jdbc:postgresql://host/db
```

Migration files: `backend/src/main/resources/db/migration/`

## SSL/TLS

### CloudFront + ACM

1. Request certificate in ACM for your domain
2. Configure CloudFront distribution with certificate
3. Set backend CORS to allow CloudFront origin

### Let's Encrypt (Self-Hosted)

Use certbot or similar for self-hosted deployments:

```bash
certbot certonly --nginx -d yourdomain.com
```

## Monitoring

### Health Endpoints

```bash
# Overall health
curl http://localhost:3000/actuator/health

# Individual components
curl http://localhost:3000/actuator/health/db
curl http://localhost:3000/actuator/health/meilisearch
```

### Logging

Backend uses SLF4J with structured logging:
- Request ID in MDC for request tracing
- User ID in MDC for authenticated requests

Log levels configurable via:
```properties
logging.level.be.transcode.morningdeck=DEBUG
```

### AWS X-Ray

If enabled, provides distributed tracing:
```properties
application.aws.xray.enabled=true
```

## Scaling Considerations

### Horizontal Scaling

The backend is stateless and can scale horizontally:
- In-memory queues are per-instance (acceptable for current scale)
- No session storage (JWT-based auth)
- Database is the shared state

### Queue Processing

Each instance runs its own queue workers. With multiple instances:
- Work distributes naturally (scheduler finds unqueued items)
- Recovery jobs handle stuck items across instances
- No coordination needed between instances

### Database Connections

Default pool size: 10 connections per instance

For many instances, consider:
- RDS Proxy for connection pooling
- Adjust `spring.datasource.hikari.maximum-pool-size`

## Security Checklist

### Production Security

- [ ] Use strong, unique JWT_SECRET (256-bit minimum)
- [ ] Enable HTTPS everywhere
- [ ] Set specific CORS origins (not `*`)
- [ ] Configure RDS in private subnet
- [ ] Use IAM roles instead of access keys where possible
- [ ] Enable RDS encryption at rest
- [ ] Review security groups (minimal ports)
- [ ] Set `application.closed-beta=true` if not public

### Secrets Management

Options:
- AWS Secrets Manager (recommended for AWS)
- Environment variables via ECS task definitions
- Kubernetes secrets
- HashiCorp Vault

## Backup and Recovery

### Database Backups

RDS automated backups:
- Enable automated backups (7-30 day retention)
- Enable Multi-AZ for automatic failover
- Test restore procedure regularly

### Search Index

Meilisearch index is rebuildable from database:
```java
// Trigger full reindex
meilisearchSyncService.reindexAll();
```

## Key Files Reference

| Component | File Path |
|-----------|-----------|
| Application properties | `backend/src/main/resources/application.properties` |
| Local properties | `backend/src/main/resources/application-local.properties` |
| Docker properties | `backend/src/main/resources/application-docker.properties` |
| Docker Compose (dev) | `docker-compose.yml` |
| Docker Compose (self-host) | `docker-compose.selfhost.yml` |
| Self-host env example | `.env.selfhost.example` |
| Backend Dockerfile | `backend/Dockerfile` |
| Frontend Dockerfile | `frontend/Dockerfile` |
| Flyway migrations | `backend/src/main/resources/db/migration/` |
| Health indicators | `backend/.../config/HealthConfig.java` |

## Related Documentation

- [Configuration](./configuration.md) - All configuration options
- [Local Setup](../development/local-setup.md) - Development environment
- [Troubleshooting](./troubleshooting.md) - Common issues
