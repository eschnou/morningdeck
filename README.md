# Morning Deck

**Your AI news analyst that reads everything, briefs you on what matters, and helps you create content from it.**

Morning Deck is an open-source, self-hostable AI-powered news intelligence platform. It aggregates content from diverse
sources (RSS, newsletters, websites, Reddit), scores and organizes it against your interests, and delivers
personalized daily briefings. Beyond consumption, it helps you transform curated news into blog posts and social media
content that matches your voice and style.

## Features

- **Multi-source aggregation** — RSS feeds, email newsletters, web scraping, Reddit
- **AI-powered scoring** — Content ranked by relevance to your defined interests
- **Daily briefings** — Scheduled summaries delivered via web or email
- **Content creation** — Generate posts for LinkedIn, Twitter/X, blogs in your voice
- **Writer profiles** — Define multiple voices for different platforms and contexts
- **Full-text search** — Find anything you've ever read
- **Self-hostable** — Own your data, use any OpenAI-compatible LLM

## Quick Start with Docker

Get Morning Deck running in 3 commands:

```bash
# 1. Download the compose file
curl -O https://raw.githubusercontent.com/eschnou/morningdeck/main/docker-compose.quickstart.yml

# 2. Create your .env file with your OpenAI key
echo "OPENAI_API_KEY=your-key-here" > .env

# 3. Start the stack
docker compose -f docker-compose.quickstart.yml up -d
```

Open http://localhost:8080 in your browser. That's it.

> **Note:** Images are available for both `linux/amd64` and `linux/arm64`.
> For advanced configuration (alternative LLM providers, Meilisearch, etc.), see [Self-Hosting](#self-hosting) below.

## Self-Hosting

For production deployments or advanced configuration, clone the repository:

```bash
git clone https://github.com/eschnou/morningdeck.git
cd morningdeck
cp .env.selfhost.example .env
# Edit .env with your settings
docker compose -f docker-compose.selfhost.yml up -d
```

### Configuration

All settings are in `.env`. The essential ones:

```bash
# Required: Your OpenAI API key
OPENAI_API_KEY=sk-...

# Optional: Use alternative LLM providers
OPENAI_BASE_URL=https://api.openai.com    # Default
# OPENAI_BASE_URL=https://openrouter.ai/api/v1
# OPENAI_BASE_URL=http://host.docker.internal:11434/v1  # Ollama

# Optional: Choose your models
OPENAI_MODEL_LITE=gpt-4o-mini   # For summaries, categorization
OPENAI_MODEL_HEAVY=gpt-4o       # For briefing generation
```

See `.env.selfhost.example` for all available options.

### Alternative LLM Providers

Morning Deck works with any OpenAI-compatible API out of the box:

| Provider   | Base URL                               | Notes                 |
|------------|----------------------------------------|-----------------------|
| OpenAI     | `https://api.openai.com`               | Default               |
| OpenRouter | `https://openrouter.ai/api/v1`         | Access to many models |
| Ollama     | `http://host.docker.internal:11434/v1` | Local, private        |
| Groq       | `https://api.groq.com/openai`          | Fast inference        |

**Want to use other providers?** Morning Deck is built on [Spring AI](https://docs.spring.io/spring-ai/reference/),
which supports many more providers natively (Anthropic, Azure OpenAI, Google Vertex AI, Amazon Bedrock, Mistral, and
more). Adding support for these requires minor code changes — see
the [Spring AI documentation](https://docs.spring.io/spring-ai/reference/api/chatmodel.html) for details.

## Development

### Prerequisites

- Java 21
- Node.js 20+
- Docker & Docker Compose
- Maven

### Local Setup

```bash
# Start PostgreSQL (and optionally Meilisearch for search)
docker compose up -d

# Backend (runs on port 3000)
cd backend
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"

# Frontend (runs on port 5173)
cd frontend
npm install
npm run dev
```

### Project Structure

```
├── backend/           # Spring Boot API (Java 21)
├── frontend/          # React SPA (TypeScript, Vite, Tailwind)
├── documentation/     # Technical documentation
├── specs/             # Product specifications
└── docker-compose.yml # Local development services
```

### Documentation

- [Local Setup Guide](./documentation/development/local-setup.md)
- [Architecture Overview](./documentation/architecture/overview.md)
- [API Reference](./documentation/development/api-reference.md)
- [Configuration Options](./documentation/operations/configuration.md)
- [Deployment Guide](./documentation/operations/deployment.md)

### Tech Stack

**Backend:** Spring Boot 3, Spring AI, PostgreSQL, Flyway, Meilisearch (optional)

**Frontend:** React 18, TypeScript, Vite, Tailwind CSS, shadcn/ui, React Query

## Community

- **Website:** [morningdeck.com](https://morningdeck.com)
- **Discord:** [Join our community](https://discord.gg/morningdeck)
- **Issues:** [GitHub Issues](https://github.com/eschnou/morningdeck/issues)

## License

Morning Deck is open source software licensed under the [GNU Affero General Public License v3.0 (AGPL-3.0)](LICENSE).

This means you can use, modify, and distribute the software, but if you run a modified version as a network service, you
must make the source code available to users of that service.
