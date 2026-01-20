# morningdeck.com — Product Brief

**Version:** 1.2
**Date:** January 2026
**Status:** Discovery & Scoping

---

## 1. Executive Summary

**morningdeck.com** is an AI-powered news intelligence platform that aggregates content from diverse sources, scores and organizes it against user-defined interests, and delivers personalized daily briefings—including AI-generated podcast-style audio summaries. Beyond consumption, it serves as a content creation hub, enabling users to transform curated news into blog posts and social media content that matches their voice and style.

**One-liner:** *Your AI news analyst that reads everything, briefs you on what matters, and helps you create content from it.*

---

## 2. Problem Statement

### The Pain Points

| Problem | Current Solutions | Why They Fall Short |
|---------|------------------|---------------------|
| **Information overload** | RSS readers, newsletters | No prioritization—users still manually scan hundreds of items |
| **Fragmented sources** | Multiple apps/subscriptions | Context-switching, no unified view |
| **No personalization** | Algorithmic feeds (Twitter, etc.) | Optimized for engagement, not user-defined relevance |
| **Time-consuming consumption** | Podcasts, news apps | Generic content, not tailored to specific interests |
| **Content creation burden** | Manual writing | High effort to transform news into posts matching personal voice |
| **No institutional memory** | Bookmarks, notes | Hard to search/retrieve past relevant content |

### The Opportunity

Replace the fragmented, manual, overwhelming news consumption workflow with an intelligent system that:
- **Reads for you** — monitors and processes all sources 24/7
- **Thinks like you** — scores content against your defined persona and criteria
- **Briefs you** — delivers a curated, personalized summary daily
- **Creates with you** — generates content that sounds like you

---

## 3. Vision & Mission

**Vision:** Become the central intelligence layer between the internet's information firehose and the individual knowledge worker.

**Mission:** Give every professional a personal news analyst that understands their interests, respects their time, and amplifies their voice.

---

## 4. Target Users

### Primary Persona: The Knowledge Professional

- **Who:** Founders, investors, consultants, researchers, content creators, executives
- **Behaviors:**
    - Needs to stay informed across multiple domains
    - Shares insights on LinkedIn, Twitter, or personal blog
    - Values depth over breadth
    - Time-constrained but intellectually curious
- **Current workflow:** 20+ browser tabs, 5+ newsletters, podcast queue they never finish

### Secondary Persona: The Content Creator

- **Who:** Bloggers, newsletter writers, thought leaders
- **Behaviors:**
    - Constantly seeking interesting angles and stories
    - Needs to publish consistently
    - Wants to maintain authentic voice at scale

---

## 5. Core Features

### 5.1 Source Management (Ingestion Layer)

| Source Type | Description | User Configuration                                                             |
|-------------|-------------|--------------------------------------------------------------------------------|
| **RSS Feeds** | Classic feed subscription | Add URL, assign categories/tags                                                |
| **Newsletter Inbox** | Dedicated email addresses per user | `{username}@inbound.morningdeck.com` — forward or subscribe directly        |
| **Website Monitoring** | Scrape specific pages on schedule | URL + custom prompt (e.g., "Extract top 30 posts from Hacker News front page") |
| **News APIs** | Integrate with news agencies/aggregators | Select sources, topics, regions                                                |
| **Social Feeds** (future) | Twitter lists, Reddit, LinkedIn | OAuth connection + filtering rules                                             |

**User Experience:**
- Simple "Add Source" wizard
- Source health dashboard (last fetch, error rate, item count)
- Bulk import/export (OPML for RSS)

---

### 5.2 Content Processing (Intelligence Layer)

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   Ingest    │───▶│   Parse &   │───▶│   Score &   │───▶│   Store &   │
│   (fetch)   │    │  Summarize  │    │    Tag      │    │   Index     │
└─────────────┘    └─────────────┘    └─────────────┘    └─────────────┘
```

**Processing Pipeline:**

1. **Fetch** — Pull new items from all sources on configurable schedules
2. **Deduplicate** — Identify same story across sources, merge/link
3. **Parse & Extract** — Clean HTML, extract main content, images, metadata
4. **Summarize** — Generate concise summary (1-2 paragraphs)
5. **Score** — Rate relevance (0-100) against user's persona and criteria
6. **Tag** — Auto-categorize (topic, sentiment, entity extraction)
7. **Store** — Persist to searchable database with full provenance

**User-Defined Scoring Criteria:**

Users define their "interest profile" through:
- **Persona description** — "I'm a startup founder interested in AI, developer tools, and B2B SaaS"
- **Topic weights** — AI (high), Climate (medium), Sports (low)
- **Entities to track** — Companies, people, technologies
- **Keywords & phrases** — Boost/penalize specific terms
- **Source trust levels** — Weight certain sources higher

---

### 5.3 Daily Briefing (Delivery Layer)

**Core Briefing:**
- Scheduled delivery (default: 7:00 AM local time)
- Configurable length: "Top 5", "Top 10", "Comprehensive"
- Delivered via: Web dashboard, email, mobile push

**Briefing Structure:**
```
📅 Your Daily Brief — January 9, 2026

🔥 TOP STORY
[Headline + 2-paragraph summary + why it matters to you]

📰 TODAY'S HIGHLIGHTS
1. [Story] — Relevance: 94%
2. [Story] — Relevance: 91%
3. [Story] — Relevance: 87%
...

📊 BY TOPIC
• AI & Machine Learning (4 stories)
• Startup Ecosystem (3 stories)
• Developer Tools (2 stories)

🔗 Full archive: morningdeck.com/archive/2026-01-09
```

**Audio Briefing (Premium Feature):**
- AI-generated podcast (10-15 minutes)
- Multi-voice format:
    - **Host/Presenter** — Guides the conversation, introduces topics
    - **Analyst/Journalist** — Provides context, background, implications
    - **Domain Expert** — Technical depth, industry perspective
- Natural conversational flow, not robotic reading
- Available as streaming audio or downloadable MP3
- Playable in-app or via podcast feed (private RSS)

---

### 5.4 Content Generation (Creation Layer)

The creation layer transforms news consumption into content production through a sophisticated system of **Writers**, **Channels**, and **Posts**.

#### 5.4.1 Writers (Voice Profiles)

A **Writer** represents a distinct voice and perspective for content creation. Users can create multiple writers for different purposes (e.g., professional LinkedIn persona vs. casual Twitter voice vs. company blog).

**Writer Definition:**

| Attribute | Description | Example |
|-----------|-------------|---------|
| **Name** | Identifier for this writer | "My LinkedIn Voice" |
| **Tone of Voice** | How the writer sounds | "Professional yet approachable, uses industry jargon sparingly, favors clarity over cleverness" |
| **Editorial Principles** | Core beliefs and values that guide content | "Always cite sources, avoid hype, focus on practical implications, acknowledge uncertainty" |
| **Interests** | Topics this writer cares about | AI/ML, startup ecosystem, developer experience, remote work |
| **Expertise Areas** | Domains where the writer has authority | B2B SaaS, developer tools, product management |
| **Example Posts** | Actual writing samples (5-10 recommended) | Past LinkedIn posts, blog articles, tweets |
| **Avoid List** | Topics, words, or styles to never use | "No engagement bait, avoid 'game-changer', never use emoji in first line" |

**Writer Learning:**
- System analyzes example posts to extract style patterns
- Identifies sentence structure, vocabulary level, rhetorical devices
- Detects signature phrases and formatting preferences
- Continuously improves based on user edits to generated drafts

**Default Writer:**
- New users get a default writer seeded from their persona and any provided writing samples
- Can be refined over time or replaced with custom writers

---

#### 5.4.2 Channels (Format Profiles)

A **Channel** defines the format and style rules for a publishing destination. Channels don't handle actual publishing—users copy the formatted content and post manually.

**Channel Configuration:**

| Attribute | Description | Required |
|-----------|-------------|----------|
| **Name** | Display name | Yes |
| **Platform Type** | Target format (LinkedIn, X, Blog, etc.) | Yes |
| **Style Rules** | Platform-specific formatting rules | Optional |
| **Default Writer** | Preferred writer for this channel | Optional |

**Supported Platform Types:**

| Platform | Format Constraints |
|----------|-------------------|
| **LinkedIn** | 3000 char limit, supports markdown-lite, professional tone expected |
| **X (Twitter)** | 280 char single post, or thread format (numbered) |
| **Facebook** | 63,206 char limit, casual tone, supports links with previews |
| **Blog/Medium** | Long-form, supports full markdown, sections and headers |
| **Newsletter** | Email-friendly formatting, concise with link to full article |
| **Generic** | No constraints, plain text output |

**Channel Style Rules:**
- Character/word limits
- Hashtag strategy (none, minimal, aggressive)
- Emoji usage rules
- Link placement preferences
- Call-to-action templates specific to platform

---

#### 5.4.3 Posts (Content Lifecycle)

A **Post** is a first-class entity representing content from draft to published.

**Post States:**

```
┌─────────┐    ┌──────────┐    ┌───────────┐
│  Draft  │───▶│ Refining │───▶│ Published │
└─────────┘    └──────────┘    └───────────┘
                    │                │
                    └────────────────┘
                      (can re-edit)
```

**Post Attributes:**

| Attribute | Description |
|-----------|-------------|
| **Source Article** | The original article that inspired this post |
| **Writer** | The voice profile used for generation |
| **Channel** | The format profile used |
| **Content** | The actual post text (with version history) |
| **Status** | Draft, Refining, Published |
| **Published URL** | Optional: link to live post (manually added by user) |
| **Published At** | When the user marked it as published |

---

#### 5.4.4 Content Creation Workflow

**Step 1: Initiate**
```
Browse Archive → Select Article → Click "Create Post" → Choose Writer + Channel
```

**Step 2: Generate**
- AI analyzes the source article
- Applies writer's tone, principles, and style patterns
- Formats for target channel's constraints
- Produces initial draft

**Step 3: Refine (Iterative)**

Users can refine the draft through:

| Method | Description |
|--------|-------------|
| **Direct Edit** | Manual text changes |
| **AI Instruction** | "Make it shorter", "Add a question at the end", "More technical" |
| **Regenerate** | Start fresh with same or different writer |
| **Tone Shift** | Quick adjustments: more formal, more casual, more provocative |

**Refinement History:**
- All versions preserved
- Can revert to any previous version
- AI learns from user edits to improve future generations

**Step 4: Copy & Publish**
- Preview final formatted content
- Copy to clipboard with one click
- Paste into target platform manually
- Mark as published (optionally add URL to live post)

**Step 5: Track**
- Post added to writer's portfolio
- Linked back to source article for reference

---

#### 5.4.5 Writer Portfolio

Each writer maintains a portfolio of published posts:

**Portfolio View:**
- Chronological list of all posts by this writer
- Filter by channel, date range, source topic
- Engagement metrics summary
- Style consistency analysis

**Use Cases:**
- Review what you've published on a topic
- Identify high-performing content patterns
- Ensure you're not repeating yourself
- Track publishing cadence

---

#### 5.4.6 Output Formats

| Format | Typical Length | Structure | Best For |
|--------|----------------|-----------|----------|
| **LinkedIn Post** | 150-300 words | Hook + insight + CTA | Professional thought leadership |
| **X/Twitter Post** | 280 chars | Punchy single take | Quick reactions, sharing links |
| **X/Twitter Thread** | 3-10 tweets | Numbered takes, narrative arc | In-depth analysis |
| **Blog Article** | 800-2000 words | Intro, sections, conclusion | Long-form thought pieces |
| **Newsletter Blurb** | 100-200 words | Quick take + context | Curated recommendations |
| **Facebook Post** | 100-250 words | Conversational, personal | Community engagement |

---

### 5.5 Archive & Search (Memory Layer)

**Capabilities:**
- Full-text search across all ingested content
- Semantic search ("articles about AI regulation in Europe")
- Filter by: date range, source, topic, relevance score, tags
- Saved searches / alerts
- Export (CSV, JSON, PDF reports)

**Use Cases:**
- "What did I read about OpenAI last quarter?"
- "Find all articles mentioning [competitor] this year"
- "Show me high-scoring articles I haven't read yet"

---

## 6. User Flows

### Flow 1: Onboarding
```
Sign Up → Define Persona → Add First Sources → Configure Briefing Schedule → Receive First Brief
```

### Flow 2: Daily Consumption
```
Wake Up → Receive Brief (email/push) → Scan Headlines → Deep-dive Interesting Items → Mark as Read/Save
```

### Flow 3: Content Creation
```
Browse Archive → Select Article → Choose Writer + Channel → Generate Draft → Refine (AI/Manual) → Approve → Publish/Schedule
```

### Flow 5: Writer Setup
```
Create Writer → Define Tone & Principles → Add Example Posts → System Analyzes Style → Writer Ready
```

### Flow 6: Channel Setup
```
Add Channel → Select Platform Type → Configure Style Rules → Save
```

### Flow 4: Research
```
Search Query → Browse Results → Filter/Refine → Export or Save Collection
```

---

## 7. Information Architecture

```
morningdeck.com
│
├── 📥 Sources
│   ├── Add Source
│   ├── Manage Sources
│   └── Source Health
│
├── 📰 Today's Brief
│   ├── Text Briefing
│   ├── Audio Briefing
│   └── Briefing History
│
├── 🗄️ Archive
│   ├── All Items
│   ├── Saved Items
│   ├── By Topic
│   └── Search
│
├── ✍️ Create
│   ├── New Post (from Article)
│   └── My Posts
│       ├── Drafts
│       └── Published
│
├── 🎭 Writers
│   ├── My Writers
│   ├── Create Writer
│   └── Writer Portfolio (per writer)
│       ├── Published Posts
│       ├── Style Analysis
│       └── Performance Metrics
│
├── 📡 Channels
│   ├── My Channels
│   ├── Add Channel
│   └── Channel Settings (per channel)
│       └── Style Rules
│
├── 👤 Profile
│   ├── Persona & Interests
│   └── Scoring Criteria
│
└── ⚙️ Settings
    ├── Briefing Schedule
    ├── Notifications
    ├── Integrations
    └── Email Addresses
```

---

## 8. Technical Considerations (High-Level)

### Key Components

| Component | Purpose | Considerations |
|-----------|---------|----------------|
| **Ingestion Workers** | Fetch content on schedules | Queue-based, scalable, respectful rate limits |
| **Processing Pipeline** | Parse, summarize, score, tag | LLM calls (batched for cost), embeddings for search |
| **Vector Database** | Semantic search | Store embeddings alongside structured data |
| **User Database** | Personas, preferences, history | Multi-tenant, secure |
| **Audio Generation** | Podcast synthesis | Text-to-speech with multiple voices, natural conversation |
| **Email Infrastructure** | Inbound (newsletters) + Outbound (briefs) | Custom domain, parsing pipeline |
| **Scheduler** | Trigger briefings at user times | Timezone-aware, reliable |
| **Writer Engine** | Analyze style, generate matching content | Style extraction from samples, continuous learning from edits |
| **Post Manager** | Draft lifecycle, version history | Track all states, store revisions |

### Data Model (New Entities)

```
┌──────────┐       ┌──────────┐       ┌──────────┐
│  Writer  │       │   Post   │       │ Channel  │
├──────────┤       ├──────────┤       ├──────────┤
│ name     │◄──────│ writer   │──────▶│ name     │
│ tone     │       │ channel  │       │ platform │
│ principles│      │ article  │       │ styleRules│
│ interests│       │ content  │       │ user     │
│ examples │       │ versions │       └──────────┘
│ user     │       │ status   │
└──────────┘       │ published│
                   │ user     │
                   └──────────┘
```

### Third-Party Dependencies
- LLM API (summarization, scoring, content generation, style analysis)
- Text-to-Speech API (podcast generation)
- News APIs (optional premium sources)
- Email service (inbound parsing + outbound delivery)

---

## 9. Monetization Model (Proposed)

| Tier | Price | Includes |
|------|-------|----------|
| **Free** | $0 | 5 sources, daily brief (text only), 7-day archive, 1 writer, 1 channel |
| **Pro** | $15/mo | Unlimited sources, audio briefing, full archive, 5 writers, 5 channels |
| **Creator** | $30/mo | Everything in Pro + unlimited writers/channels, advanced AI refinement, style analysis |
| **Team** | $25/user/mo | Shared sources, team briefs, shared writers, brand voice governance |

**Feature Breakdown by Tier:**

| Feature | Free | Pro | Creator | Team |
|---------|------|-----|---------|------|
| Writers | 1 | 5 | Unlimited | Unlimited + Shared |
| Channels | 1 | 5 | Unlimited | Unlimited |
| AI Refinement | Basic | Standard | Advanced | Advanced |
| Post History | 30 days | 1 year | Unlimited | Unlimited |
| Style Analysis | No | Basic | Advanced | Advanced |

---

## 10. Success Metrics

### North Star Metric
**Daily Brief Open Rate** — % of users who engage with their brief each day

### Supporting Metrics

| Category | Metric | Target |
|----------|--------|--------|
| **Engagement** | Brief open rate | >60% |
| **Engagement** | Audio listen-through rate | >40% |
| **Retention** | Weekly active users | >70% of registered |
| **Creation** | Posts created/user/month | >8 |
| **Creation** | Draft-to-publish conversion | >50% |
| **Creation** | Posts published/user/month | >4 |
| **Quality** | AI refinement iterations/post | <3 (target: get it right faster) |
| **Growth** | Organic referrals | >30% of signups |
| **Satisfaction** | NPS | >50 |

---

## 11. Competitive Landscape

| Competitor | Strengths | morningdeck.com Differentiation                    |
|------------|-----------|----------------------------------------------------|
| **Feedly** | Established, good RSS | No AI prioritization, no audio, no content creation |
| **Matter** | Clean reading experience | Limited sources, no briefing, no creation          |
| **Artifact** (RIP) | AI-powered news | Shut down; was consumer-focused, not professional  |
| **Perplexity** | AI search | Pull-based, not push briefing; no source curation  |
| **Superhuman (for news)** | Speed | Email-only; doesn't aggregate other sources        |
| **Pocket** | Save for later | No AI, no prioritization, no creation              |
| **Buffer/Hootsuite** | Multi-platform publishing | No content sourcing, no AI generation, scheduling-only |
| **Jasper/Copy.ai** | AI writing | No news context, generic content, no voice training |
| **Typefully** | Twitter/LinkedIn drafts | Single-purpose, no news integration, limited platforms |

**morningdeck.com's moat:**
- **End-to-end workflow:** Ingestion → Intelligence → Briefing → Creation (copy to publish)
- **Deep personalization:** Writers capture authentic voice, not generic AI content
- **Voice consistency:** Train multiple writers for different contexts and platforms
- **Content-to-creation loop:** Unlike pure generators (Jasper), morningdeck connects curated news directly to authentic creation
- **Self-hostable:** Open-source friendly, no vendor lock-in, own your data

---


*This is a living document. Last updated: January 2026*
