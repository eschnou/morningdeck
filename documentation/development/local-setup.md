# Local Development Setup

This guide covers setting up a local development environment for Morning Deck.

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Java | 21+ | Backend runtime |
| Maven | 3.9+ | Backend build |
| Node.js | 18+ | Frontend runtime |
| npm | 9+ | Frontend packages |
| Docker | 24+ | Local services |

## Quick Start

```bash
# 1. Start local services (PostgreSQL + Meilisearch)
docker-compose up -d

# 2. Start backend (port 3000)
cd backend
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"

# 3. Start frontend (port 5173)
cd frontend
npm install
npm run dev
```

Open http://localhost:5173 in your browser.

## Step-by-Step Setup

### 1. Clone Repository

```bash
git clone <repository-url>
cd morningdeck
```

### 2. Start Local Services

```bash
docker-compose up -d
```

This starts:

| Service | Port | UI/Access |
|---------|------|-----------|
| PostgreSQL | 5432 | `psql -h localhost -U postgres -d morningdeck` |
| Meilisearch | 7700 | http://localhost:7700 |

**Database credentials:**
- Host: `localhost`
- Port: `5432`
- Database: `morningdeck`
- Username: `postgres`
- Password: `postgres`

**Meilisearch:**
- Host: http://localhost:7700
- API Key: `masterKey` (development only)

### 3. Configure Environment

The backend uses `application-local.properties` for local development. Default settings work out of the box.

**Required for full functionality:**

Create `backend/src/main/resources/application-local.properties` overrides:

```properties
# OpenAI API key (required for AI processing)
spring.ai.openai.api-key=sk-your-key-here

# JWT secret (any base64 string for local dev)
spring.security.jwt.secret=bXlzZWNyZXRrZXlmb3Jsb2NhbGRldmVsb3BtZW50
```

### 4. Start Backend

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"
```

Backend runs on http://localhost:3000

**Verify:**
- Health: http://localhost:3000/actuator/health
- API Docs: http://localhost:3000/swagger-ui.html

### 5. Start Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend runs on http://localhost:5173

## Development Workflow

### Backend

```bash
# Compile
mvn clean compile

# Run with live reload (via spring-boot-devtools)
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"

# Run tests
mvn test

# Run single test class
mvn test -Dtest=UserIT

# Build JAR
mvn clean package

# Run integration tests
mvn verify
```

### Frontend

```bash
# Development server with hot reload
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview

# Lint
npm run lint
```

## Database Management

### Access PostgreSQL

```bash
# Via Docker
docker exec -it morningdeck-db psql -U postgres -d morningdeck

# Via psql (if installed locally)
psql -h localhost -U postgres -d morningdeck
```

### Reset Database

```bash
# Stop services
docker-compose down

# Remove volumes (deletes data)
docker-compose down -v

# Restart fresh
docker-compose up -d
```

### View Migrations

Migrations are in `backend/src/main/resources/db/migration/`

```bash
# Check migration status
cd backend
mvn flyway:info
```

## Meilisearch (Optional)

Meilisearch provides instant search. It's **optional** - the app works without it.

### Dev UI

Open http://localhost:7700 to:
- Browse indexes
- Test search queries
- View documents

### Disable Meilisearch

To run without Meilisearch (faster startup):

```bash
# Only start PostgreSQL
docker-compose up -d db
```

Add to `application-local.properties`:
```properties
meilisearch.enabled=false
```

## Email (Development)

By default, local development uses the `logs` email sender - emails are printed to console instead of being sent.

To test with real emails, configure SMTP:

```properties
application.email.sender=smtp
spring.mail.host=smtp.mailtrap.io
spring.mail.port=587
spring.mail.username=your-mailtrap-user
spring.mail.password=your-mailtrap-pass
```

## Common Issues

### Port Already in Use

```bash
# Find process using port 3000
lsof -i :3000

# Kill process
kill -9 <PID>
```

### Database Connection Failed

```bash
# Check if PostgreSQL is running
docker-compose ps

# View PostgreSQL logs
docker-compose logs db
```

### OpenAI API Errors

Without a valid OpenAI API key:
- Source fetching works
- AI processing (summaries, scores) fails
- Items remain in NEW status

For testing without OpenAI, items can be manually moved to DONE status.

### Frontend Can't Connect to Backend

Check CORS settings and ensure backend is running on port 3000.

Frontend expects backend at `http://localhost:3000` by default.

## IDE Setup

### IntelliJ IDEA (Backend)

1. Open `backend/` folder
2. Import as Maven project
3. Set Project SDK to Java 21
4. Install Lombok plugin
5. Enable annotation processing

**Run configuration:**
- Main class: `MorningdeckServerApplication`
- Active profiles: `local`

### VS Code (Frontend)

Recommended extensions:
- ESLint
- Tailwind CSS IntelliSense
- TypeScript and JavaScript
- Prettier

## Service URLs

| Service | URL |
|---------|-----|
| Frontend | http://localhost:5173 |
| Backend API | http://localhost:3000 |
| Swagger UI | http://localhost:3000/swagger-ui.html |
| OpenAPI Spec | http://localhost:3000/api-docs |
| Health Check | http://localhost:3000/actuator/health |
| Meilisearch UI | http://localhost:7700 |

## Related Documentation

- [Testing](./testing.md) - Test strategy
- [API Reference](./api-reference.md) - REST endpoints
- [Configuration](../operations/configuration.md) - All configuration options
