// Video-only stand-in for "@/lib/api": the real pages and contexts call these methods,
// and get fixture data back. Each call holds a Remotion render handle until React has
// had a chance to commit the resulting state, so no frame is captured mid-loading.
import { continueRender, delayRender } from "remotion";
import { BRIEF_ID, briefs, newsItems, reports, sources, user } from "./fixtures";

// AuthContext only fetches the user when a token is present.
try {
  localStorage.setItem("auth_token", "demo");
} catch {
  // Storage unavailable — AuthContext will render signed-out, which scenes don't rely on.
}

const page = <T,>(content: T[]) => ({
  content,
  totalElements: content.length,
  totalPages: 1,
  number: 0,
  size: content.length,
});

function resolve<T>(value: T): Promise<T> {
  const handle = delayRender("mock api");
  return new Promise((res) => {
    res(value);
    setTimeout(() => continueRender(handle), 60);
  });
}

const byId = (id: string) => newsItems.find((n) => n.id === id) ?? newsItems[0];

export const apiClient = {
  setToken: (_token: string | null) => {},
  getPublicConfig: () => resolve({ emailVerificationEnabled: true, selfHostedMode: false }),
  getCurrentUser: () => resolve(user),
  getDayBriefs: () => resolve(page(briefs)),
  getDayBrief: (id: string) => resolve(briefs.find((b) => b.id === id) ?? briefs[0]),
  getBriefingSources: () => resolve(page(sources)),
  getSources: () => resolve(page(sources)),
  getSource: (id: string) => resolve(sources.find((s) => s.id === id) ?? sources[0]),
  getBriefingItems: () => resolve(page([...newsItems].sort((a, b) => (b.score ?? 0) - (a.score ?? 0)))),
  getNewsItems: () => resolve(page(newsItems)),
  getNewsItem: (id: string) => resolve(byId(id)),
  toggleRead: (id: string) => resolve(byId(id)),
  toggleSaved: (id: string) => resolve(byId(id)),
  getDayBriefReports: () => resolve(page(reports)),
  getDayBriefReport: () => resolve(reports[0]),
  getLatestReports: () => resolve(reports),
  reorderBriefs: () => resolve(undefined),
  getSubscription: () => resolve({ plan: "PRO", creditsBalance: 4200, monthlyCredits: 5000 }),
  BRIEF_ID,
};
