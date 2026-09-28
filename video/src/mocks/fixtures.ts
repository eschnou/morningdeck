import type { DailyReportDTO, DayBriefDTO, NewsItemDTO, ReportItemDTO, SourceDTO } from "@/types";

const now = Date.now();
const ago = (minutes: number) => new Date(now - minutes * 60_000).toISOString();

export const BRIEF_ID = "3f6c2a9e-8b1d-4c7a-9e2f-1a5b6c7d8e9f";

export const briefs: DayBriefDTO[] = [
  ["AI & Dev Tools", "Daily at 7:00"],
  ["Startup Ecosystem", "Daily at 7:30"],
  ["Climate Tech", "Weekly"],
  ["Competitor Watch", "Daily at 8:00"],
].map(([title], i) => ({
  id: i === 0 ? BRIEF_ID : `brief-${i}`,
  title,
  description: null,
  briefing: "I'm a startup founder interested in AI, developer tools and B2B SaaS.",
  frequency: i === 2 ? "WEEKLY" : "DAILY",
  scheduleDayOfWeek: i === 2 ? "MONDAY" : null,
  scheduleTime: "07:00:00",
  timezone: "Europe/Brussels",
  status: "ACTIVE",
  lastExecutedAt: ago(90),
  sourceCount: 12,
  createdAt: ago(60 * 24 * 40),
  emailDeliveryEnabled: true,
  position: i,
}));

const source = (s: Partial<SourceDTO> & Pick<SourceDTO, "id" | "name" | "type" | "url">): SourceDTO => ({
  briefingId: BRIEF_ID,
  briefingTitle: "AI & Dev Tools",
  extractionPrompt: null,
  emailAddress: null,
  status: "ACTIVE",
  tags: [],
  lastFetchedAt: ago(4),
  lastError: null,
  itemCount: 0,
  unreadCount: 0,
  createdAt: ago(60 * 24 * 30),
  ...s,
});

export const sources: SourceDTO[] = [
  source({ id: "s1", name: "Hacker News", type: "WEB", url: "https://news.ycombinator.com", itemCount: 1284, unreadCount: 42, tags: ["tech", "startups"] }),
  source({ id: "s2", name: "AI Insider Newsletter", type: "EMAIL", url: "", emailAddress: "alex.ai@inbound.morningdeck.com", itemCount: 316, unreadCount: 7, tags: ["newsletter"] }),
  source({ id: "s3", name: "r/MachineLearning", type: "REDDIT", url: "https://reddit.com/r/MachineLearning", itemCount: 872, unreadCount: 31, tags: ["research"] }),
  source({ id: "s4", name: "Dev Weekly", type: "RSS", url: "https://devweekly.example.com/feed.xml", itemCount: 540, unreadCount: 12, tags: ["engineering"] }),
];

type Seed = [title: string, source: string, score: number, topics: string[], summary: string, reasoning: string];

const seeds: Seed[] = [
  [
    "Open-weight coding models now rival closed APIs on real-world benchmarks",
    "Hacker News", 94, ["AI", "Open Source", "Dev Tools"],
    "A new round of evaluations shows open-weight models matching proprietary APIs on repository-level coding tasks, at a fraction of the inference cost.",
    "Directly relevant to your focus on AI developer tools: lower-cost open models change build-vs-buy decisions for coding assistants.",
  ],
  [
    "Why AI agents are moving from demos to production in B2B SaaS",
    "AI Insider Newsletter", 91, ["AI Agents", "B2B SaaS"],
    "Companies report that narrow, well-scoped agents handling support and ops workflows are the first to reach production reliability.",
    "Matches two of your core interests — AI agents and B2B SaaS — with concrete adoption data.",
  ],
  [
    "Postgres adds native vector search improvements for RAG workloads",
    "Dev Weekly", 87, ["Databases", "RAG"],
    "The latest release improves index build times and filtered vector queries, making Postgres a stronger default for retrieval pipelines.",
    "Relevant to your dev-tools interest and to anyone running retrieval on an existing Postgres stack.",
  ],
  [
    "Seed rounds for developer-tool startups hit a two-year high",
    "Hacker News", 82, ["Startups", "Funding"],
    "Investors are concentrating early-stage bets on AI-native developer tooling, according to quarterly deal data.",
    "Useful market signal for a founder in developer tools.",
  ],
  [
    "Small language models are quietly winning on-device use cases",
    "r/MachineLearning", 78, ["AI", "Edge"],
    "Sub-5B parameter models are proving good enough for summarisation and classification running fully on laptops and phones.",
    "Related to your AI focus; moderate relevance to B2B products.",
  ],
  [
    "The hidden cost of context switching for engineering teams",
    "Dev Weekly", 64, ["Productivity"],
    "A survey of 2,000 engineers links frequent tool switching to measurable drops in deep-work time.",
    "Tangential: productivity topic, not specific to your tracked interests.",
  ],
  [
    "Transfer window: record fees as clubs splash on midfielders",
    "Hacker News", 11, ["Sports"],
    "Several clubs broke transfer records in the final days of the window.",
    "Sports news — outside every interest in your profile.",
  ],
  [
    "Celebrity chef opens a third restaurant downtown",
    "AI Insider Newsletter", 6, ["Lifestyle"],
    "The new location focuses on seasonal tasting menus.",
    "Lifestyle content with no connection to your profile.",
  ],
  [
    "10 productivity hacks you have definitely read before",
    "r/MachineLearning", 18, ["Listicle"],
    "A roundup of familiar tips on inbox zero, time-blocking and standing desks.",
    "Generic listicle with little new information for you.",
  ],
];

export const newsItems: NewsItemDTO[] = seeds.map(([title, sourceName, score, topics, summary, scoreReasoning], i) => ({
  id: `n${i + 1}`,
  title,
  link: "https://example.com/article",
  author: ["Sarah Kim", "Daniel Ortiz", "Priya Raman", "Tom Becker", "Ana Silva", "Chris Moll", null, null, null][i],
  publishedAt: ago(35 + i * 47),
  content:
    "**Why it matters.** " + summary + "\n\nTeams evaluating their stack this quarter should revisit assumptions about cost, latency and vendor lock-in. Early adopters report faster iteration cycles and more predictable spend.",
  summary,
  tags: { topics },
  sourceId: sources.find((s) => s.name === sourceName)?.id ?? "s1",
  sourceName,
  readAt: i > 3 ? ago(10) : null,
  saved: i === 1,
  score,
  scoreReasoning,
  createdAt: ago(30 + i * 47),
}));

// Extra headlines for the "too much news" opening montage.
export const noiseTitles = [
  "Breaking: markets react to overnight rate decision",
  "The 47 newsletters every founder should read",
  "Thread: everything announced at this week's keynote",
  "Opinion: is remote work over (again)?",
  "Weekly roundup: 120 links you missed",
  "Live blog: product launch event",
  "You won't believe these 12 AI tools",
  "Morning digest #1,284",
];

export const reportItems: ReportItemDTO[] = newsItems
  .filter((n) => (n.score ?? 0) >= 70)
  .map((n, i) => ({
    id: `r${i}`,
    newsItemId: n.id,
    title: n.title,
    link: n.link,
    sourceName: n.sourceName,
    summary: n.summary,
    score: n.score ?? 0,
    position: i + 1,
  }));

export const reports: DailyReportDTO[] = [0, 1, 2].map((d) => ({
  id: `report-${d}`,
  dayBriefId: BRIEF_ID,
  dayBriefTitle: "AI & Dev Tools",
  dayBriefDescription: null,
  generatedAt: new Date(now - d * 86_400_000).toISOString(),
  status: "GENERATED",
  items: reportItems,
  itemCount: reportItems.length,
}));

export const user = {
  id: "u1",
  username: "alex",
  name: "Alex Morgan",
  email: "alex@example.com",
  emailVerified: true,
  role: "USER",
};
