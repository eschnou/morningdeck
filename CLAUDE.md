# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Morning Deck is a full-stack application with:
- **Backend**: Spring Boot 3 service (Java 21) - see `backend/CLAUDE.md` for details
- **Frontend**: React + TypeScript with Vite - see `frontend/CLAUDE.md` for details

## Quick Start

```bash
# Start PostgreSQL and Meilisearch
docker-compose up -d

# Backend (port 3000)
cd backend && mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"

# Frontend (port 5173)
cd frontend && npm run dev
```

## Local Services

The `docker-compose.yml` starts the following services:

| Service | Port | Description |
|---------|------|-------------|
| PostgreSQL | 5432 | Database (user: postgres, pass: postgres, db: morningdeck) |
| Meilisearch | 7700 | Search engine with dev UI at http://localhost:7700 |

### Meilisearch (Optional Feature)

Meilisearch provides instant search-as-you-type for articles. It's **enabled by default** in local development (`application-local.properties`).

**Dev UI**: Open http://localhost:7700 to explore the search index and test queries.

**To disable** (e.g., for faster startup): Set `meilisearch.enabled=false` in `application-local.properties` or run only PostgreSQL:
```bash
docker-compose up -d db
```

**Self-hosted deployments**: Meilisearch is optional. The app works without it - users just won't have instant search. Set `MEILISEARCH_ENABLED=false` in production or simply don't configure it.

## Self-Hosted Deployment

For self-hosting with Docker (uses pre-built images from ghcr.io):

```bash
# 1. Copy the example environment file
cp .env.selfhost.example .env

# 2. Edit .env and set your OPENAI_API_KEY (required)
nano .env

# 3. Start all services (images are pulled automatically)
docker compose -f docker-compose.selfhost.yml up -d

# To build locally instead: docker compose -f docker-compose.selfhost.yml up -d --build
```

The app will be available at http://localhost:8080. See `.env.selfhost.example` for all configuration options including alternative LLM providers (OpenRouter, Ollama, Groq).

## Repository Structure

```
├── .github/workflows/          # CI/CD (Docker image publishing)
├── backend/                    # Spring Boot API server (Java 21, Maven)
├── frontend/                   # React SPA (TypeScript, Vite, Tailwind)
├── specs/                      # Specification documents
│   ├── product.md              # Business-level product overview
│   └── <feature>/              # Feature specifications
├── docker-compose.yml          # Local PostgreSQL + Meilisearch (dev)
└── docker-compose.selfhost.yml # Self-hosted deployment (all services)
```

## Development Process

### Steering Documents
The `./specs` folder contains steering documents for the project:
- `./specs/product.md` - Business-level overview of the application

### Spec-Driven Development
For each new major feature, create a new folder in `./specs` containing:
- `requirements.md` - Detailed description of the feature requirements
- `design.md` - Detailed design of the feature
- `tasks.md` - List of tasks, grouped by phases, to implement the feature

### Documentation
When a user request is completed, create or update documentation in `./documentation` to reflect the changes:
1. Keep one file per functional or technical domain area
2. **Always update `./documentation/index.md`** when adding new documentation files
3. Before finishing any task that adds or modifies features, check if documentation needs updating
4. Reference `./documentation/index.md` to see existing documentation topics

## Shared Coding Guidelines

### General Principles
- Prefer editing existing files over creating new ones
- Follow established patterns in the codebase
- Write self-documenting code with clear naming
- Include tests for new functionality

### Error Handling
- Use exceptions for error conditions (backend)
- Use proper error boundaries and toast notifications (frontend)

### API Contract
- Backend exposes REST API on `/api/**` endpoints
- Frontend consumes via `apiClient` singleton
- API documentation available at `/swagger-ui.html` and `/api-docs`
